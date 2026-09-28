package com.ttech.navi.nav

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.ttech.navi.BuildConfig
import com.ttech.navi.NaviApplication
import com.ttech.navi.NaviContainer
import com.ttech.navi.domain.Fix
import com.ttech.navi.domain.NaviException
import com.ttech.navi.domain.NaviSettings
import com.ttech.navi.domain.NavSession
import com.ttech.navi.domain.Phrases
import com.ttech.navi.domain.SessionUpdate
import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.LatLon
import com.ttech.track.domain.TrackPoint
import com.ttech.track.location.GpsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ナビの心臓部。案内中だけ動く前面サービス。
 *  - GPSを1秒ごとに受け取り、[NavSession] に渡して、次の曲がり角などを声で案内する
 *  - ルートから外れたら、ルートを引き直す
 *  - 一定の距離ごとに、いまの市区町村を調べ、県・市をまたいだら声で知らせる
 *  - 到着したら、走った距離とかかった時間を声で知らせる
 * 画面を閉じても案内を続けるため、状況は [NavState] に出し、画面はそれを見て描く。
 */
class NavService : Service(), GpsTracker.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: NaviContainer
    private var settings = NaviSettings()
    private var settingsJob: Job? = null
    private var speaker: Speaker? = null
    private var gps: GpsTracker? = null
    private var simulation: Job? = null
    private var session: NavSession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastFix: Fix? = null
    private var lastSpoken: String? = null
    private var simulated = false
    private var finished = false
    private var rerouting = false
    private var lastRerouteAt = -REROUTE_COOLDOWN_MS
    private var regionText: String? = null
    private var regionQuerying = false
    private var lastRegionQueryAt = -REGION_INTERVAL_MS
    private var lastRegionQueryPos: LatLon? = null
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as NaviApplication).container
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_STOP -> {
                stopNavigation()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        val pending = NavState.pending
        if (pending == null || session != null) {
            if (session == null) stopSelf()
            return
        }
        NavState.pending = null
        if (!enterForeground("案内を始めています")) {
            stopSelf()
            return
        }
        NavState.notice.value = null
        simulated = BuildConfig.DEBUG && pending.simulateSpeedMps != null
        val startMs = System.currentTimeMillis()
        val s = NavSession(pending.route, pending.destination, startMs)
        session = s

        scope.launch {
            settings = container.settings.current()
            speaker = Speaker(this@NavService).also { it.enabled = settings.voice; it.rate = settings.speechRate }
            // 設定を変えたら、その場で反映する(音声のオン・オフ、読み上げの速さ)
            settingsJob = launch {
                container.settings.settings.collect { st ->
                    settings = st
                    speaker?.enabled = st.voice
                    speaker?.rate = st.speechRate
                }
            }
            acquireWakeLock()
            speak(Phrases.start(pending.route, startMs))
            announceWeather(pending.route, startMs, pending.briefing)
            if (simulated) startSimulation(pending.simulateSpeedMps!!) else startGps()
            publish(null, null)
        }
    }

    /** 天気の案内。画面で先に作ってあればそれを読み、無ければここで作る(取れなければ、その旨だけ伝える) */
    private fun announceWeather(route: com.ttech.navi.domain.Route, startMs: Long, prepared: List<String>?) {
        if (!settings.weatherBriefing) return
        if (prepared != null) {
            prepared.forEach { speak(it, ttlMs = 60_000) }
            return
        }
        scope.launch {
            try {
                container.briefing.briefing(route, startMs).forEach { speak(it, ttlMs = 60_000) }
            } catch (e: NaviException) {
                speak("天気予報を取得できませんでした。", ttlMs = 60_000)
            }
        }
    }

    private fun startGps() {
        val tracker = GpsTracker(this, intervalMs = 1000L)
        gps = tracker
        if (!tracker.start(this)) {
            NavState.notice.value = "GPSを使えません。端末の位置情報をオンにして、権限を確認してください"
            stopNavigation()
            stopSelf()
        }
    }

    /** デバッグ用: ルートの上を一定の速さで走ったことにして、1秒ぶんの移動を0.1秒ごとに進める(10倍速) */
    private fun startSimulation(mps: Double) {
        session ?: return
        simulation = scope.launch {
            var p = 0.0
            var t = System.currentTimeMillis()
            var current = session?.route
            while (isActive && !finished) {
                val route = session?.route ?: break
                // ルートを引き直したら、新しいルートの先頭から走り直す
                if (route !== current) {
                    current = route
                    p = 0.0
                }
                val here = route.line.pointAt(p)
                val ahead = route.line.pointAt(p + 5.0)
                handleFix(Fix(t, here.lat, here.lon, mps, GeoMath.bearingDegrees(here, ahead), 5.0))
                p += mps
                t += 1000
                if (p > route.distanceM + 40.0) break
                delay(SIM_TICK_MS)
            }
        }
    }

    override fun onPoint(point: TrackPoint) {
        handleFix(withMotion(Fix(point.timeMs, point.lat, point.lon, point.speed, point.bearing, point.hAcc)))
    }

    /**
     * 端末が速さ・向きを返さないとき(測位が始まった直後・一部の端末)は、前の位置との差から求める。
     * 向きは3m以上動いたときだけ(止まっている間の位置のふらつきで、向きが回らないように)
     */
    private fun withMotion(fix: Fix): Fix {
        val prev = lastFix ?: return fix
        val dtSec = (fix.timeMs - prev.timeMs) / 1000.0
        if (dtSec <= 0.0) return fix
        val d = GeoMath.distanceMeters(prev.latLon, fix.latLon)
        val speed = fix.speedMps ?: if (dtSec <= 10.0) d / dtSec else null
        val bearing = fix.bearing ?: if (d >= 3.0) GeoMath.bearingDegrees(prev.latLon, fix.latLon) else prev.bearing
        return fix.copy(speedMps = speed, bearing = bearing)
    }

    private fun handleFix(fix: Fix) {
        val s = session ?: return
        if (finished) return
        lastFix = fix
        val update = s.onFix(fix)
        update.guidance.announcements.forEach { speak(it.text) }
        if (BuildConfig.DEBUG && (update.guidance.announcements.isNotEmpty() || update.guidance.offRoute)) {
            val g = update.guidance
            Log.i(TAG, "位置=(%.5f,%.5f) 進み=%.0fm ルートから=%.0fm 次まで=%.0fm 案内=%s はずれ=%s".format(fix.lat, fix.lon, g.progressM, g.offsetM, g.distToNextM ?: -1.0, g.announcements.map { it.maneuverIndex }, g.offRoute))
        }
        if (update.guidance.offRoute) maybeReroute(fix, s)
        maybeCheckRegion(fix, s)
        val arrival = update.arrival
        if (arrival != null && !finished) {
            finished = true
            speak(arrival.speech, ttlMs = 120_000)
            gps?.stop()
            simulation?.cancel()
            releaseWakeLock()
            notify("目的地に到着しました", "走行 ${Phrases.distanceExact(arrival.distanceM)} ・ ${Phrases.duration(arrival.durationMs / 1000.0)}", finished = true)
        }
        publish(fix, update)
        if (!finished) updateNotification(update)
    }

    private fun maybeReroute(fix: Fix, s: NavSession) {
        val now = SystemClock.elapsedRealtime()
        if (rerouting || now - lastRerouteAt < REROUTE_COOLDOWN_MS) return
        rerouting = true
        lastRerouteAt = now
        speak("ルートから外れました。ルートを再検索します。")
        scope.launch {
            try {
                val route = container.osrm.route(fix.latLon, s.destination.latLon)
                s.reroute(route)
                NavState.notice.value = null
                speak("新しいルートで案内します。目的地まで、${Phrases.distanceExact(route.distanceM)}です。")
            } catch (e: NaviException) {
                NavState.notice.value = e.message
            } finally {
                rerouting = false
            }
        }
    }

    /** 進んだ距離と間隔の条件を満たしたら、いまの市区町村を調べる。県・市をまたいだと確かめられたら、声で知らせる */
    private fun maybeCheckRegion(fix: Fix, s: NavSession) {
        if (!settings.regionAnnouncements || regionQuerying) return
        val now = SystemClock.elapsedRealtime()
        val interval = if (s.regions.checking) REGION_RECHECK_MS else REGION_INTERVAL_MS
        if (now - lastRegionQueryAt < interval) return
        val last = lastRegionQueryPos
        if (last != null && !s.regions.checking && GeoMath.distanceMeters(last, fix.latLon) < REGION_MIN_MOVE_M) return
        regionQuerying = true
        lastRegionQueryAt = now
        lastRegionQueryPos = fix.latLon
        scope.launch {
            try {
                val r = container.gsi.reverse(fix.lat, fix.lon)
                if (r != null) {
                    val region = container.muni.regionOf(r.muniCd)
                    regionText = region.prefecture + (region.city?.let { " $it" } ?: "")
                    s.onRegion(region)?.let { speak(it, ttlMs = 30_000) }
                }
            } catch (_: Exception) {
                // 通信できなくても、案内は続ける。次の機会にまた調べる
            } finally {
                regionQuerying = false
            }
        }
    }

    private fun speak(text: String, ttlMs: Long = 15_000L) {
        lastSpoken = text
        speaker?.speak(text, ttlMs)
    }

    private fun publish(fix: Fix?, update: SessionUpdate?) {
        val s = session ?: return
        val g = update?.guidance
        val now = fix?.timeMs ?: System.currentTimeMillis()
        NavState.view.value = NavView(
            route = s.route,
            destination = s.destination,
            position = (fix ?: lastFix)?.latLon,
            bearing = (fix ?: lastFix)?.bearing,
            speedKmh = (fix ?: lastFix)?.speedMps?.times(3.6),
            progressM = g?.progressM ?: s.engine.progressM,
            remainingM = g?.remainingM ?: s.route.distanceM,
            remainingS = g?.remainingS ?: s.route.durationS,
            etaMs = g?.let { now + (it.remainingS * 1000).toLong() },
            next = g?.next ?: s.route.maneuvers.firstOrNull(),
            distToNextM = g?.distToNextM,
            afterNext = g?.afterNext,
            gapM = g?.gapM,
            regionText = regionText,
            lastSpoken = lastSpoken,
            offRoute = g?.offRoute == true || rerouting,
            rerouting = rerouting,
            gpsWaiting = fix == null && lastFix == null,
            arrival = s.arrival,
            simulated = simulated,
        )
    }

    private fun updateNotification(u: SessionUpdate) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        lastNotifyAt = now
        val g = u.guidance
        val next = g.next?.let { m -> "${Phrases.distance(g.distToNextM ?: 0.0)}先、${Phrases.maneuver(m)}" } ?: "案内中"
        notify("案内中: ${session?.destination?.name ?: ""}", "$next ・ 残り ${Phrases.distanceExact(g.remainingM)}")
    }

    private fun notify(title: String, text: String, finished: Boolean = false) {
        getSystemService(NotificationManager::class.java).notify(Notifications.ID_STATUS, Notifications.status(this, title, text, finished))
    }

    /** 位置情報の権限が無いと、種類が location の前面サービスは始められない */
    private fun enterForeground(text: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            NavState.notice.value = "位置情報の権限が必要です"
            return false
        }
        return try {
            val n = Notifications.status(this, "案内中", text)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notifications.ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(Notifications.ID_STATUS, n)
            }
            true
        } catch (e: Exception) {
            NavState.notice.value = "バックグラウンドで動かせませんでした: ${e.message}"
            false
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "navi:guidance").apply { acquire(8 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopNavigation() {
        finished = true
        gps?.stop()
        gps = null
        simulation?.cancel()
        settingsJob?.cancel()
        releaseWakeLock()
        speaker?.shutdown()
        speaker = null
        session = null
        NavState.view.value = null
    }

    override fun onDestroy() {
        stopNavigation()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NaviSvc"
        const val ACTION_START = "com.ttech.navi.START"
        const val ACTION_STOP = "com.ttech.navi.STOP"
        private const val REROUTE_COOLDOWN_MS = 20_000L
        private const val REGION_INTERVAL_MS = 10_000L
        private const val REGION_RECHECK_MS = 3_000L
        private const val REGION_MIN_MOVE_M = 150.0
        private const val NOTIFY_INTERVAL_MS = 10_000L
        private const val SIM_TICK_MS = 100L

        /** 案内を始める(先に [NavState.pending] に指示を入れておく) */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, NavService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, NavService::class.java).setAction(ACTION_STOP))
        }
    }
}
