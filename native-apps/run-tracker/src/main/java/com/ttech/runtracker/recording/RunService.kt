package com.ttech.runtracker.recording

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.ttech.runtracker.RunApplication
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.RunState
import com.ttech.runtracker.domain.RunRecorder
import com.ttech.runtracker.domain.RunSettings
import com.ttech.runtracker.domain.RunSummary
import com.ttech.track.domain.Format
import com.ttech.track.domain.TrackPoint
import com.ttech.track.location.GpsStatus
import com.ttech.track.location.GpsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ランを計測するサービス。画面を閉じても、ポケットに入れても、GPSを1秒ごとに受け取って記録を続ける。
 *  - 開始: 画面の「スタート」から。前面のサービス(通知つき)になる
 *  - 一時停止・再開: 画面か、通知のボタンから
 *  - 終了: 統計を計算して保存し、出発地の名前と、地図の画像を作る。終わったらサービスも終わる
 */
class RunService : Service(), GpsTracker.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: RunContainer
    private lateinit var recorder: RunRecorder
    private lateinit var gps: GpsTracker
    private var wakeLock: PowerManager.WakeLock? = null
    private var settings = RunSettings()
    private var settingsJob: Job? = null
    private var finishing = false

    private val ticker = object : Runnable {
        override fun run() {
            if (recorder.isActive) {
                publish()
                refreshNotification()
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as RunApplication).container
        Notifications.ensureChannels(this)
        recorder = RunRecorder(container.files)
        gps = GpsTracker(this, intervalMs = 1000L)
        if (!enterForeground()) {
            stopSelf()
            return
        }
        RunState.serviceRunning.value = true
        settingsJob = scope.launch { container.settings.settings.collect { settings = it } }
        handler.postDelayed(ticker, TICK_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> {
                recorder.pause()
                publish()
                refreshNotification()
            }
            ACTION_RESUME -> {
                recorder.resume()
                publish()
                refreshNotification()
            }
            ACTION_STOP -> stopRecording()
            // 計測が終わっている(または始まっていない)のに、システムが再起動させたときは、何もせず終わる
            else -> if (!recorder.isActive && !finishing) stopSelf()
        }
        // 強制終了されたら、そこまでの点はファイルに残っている。次にアプリを開いたとき、復旧する
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder.isActive || finishing) return
        recorder.start()
        if (!gps.start(this)) {
            RunState.notice.value = "GPSを使えません。端末の位置情報をオンにして、権限を確認してください"
            recorder.finish(minDistanceM = Int.MAX_VALUE, weightKg = null)
            stopSelf()
            return
        }
        RunState.notice.value = null
        RunState.finishedId.value = null
        acquireWakeLock()
        publish()
        refreshNotification()
    }

    private fun stopRecording() {
        if (!recorder.isActive || finishing) return
        finishing = true
        gps.stop()
        releaseWakeLock()
        val startMs = recorder.live()?.startTimeMs ?: System.currentTimeMillis()
        val summary = recorder.finish(settings.minDistanceM, container.body.weightAt(startMs))
        RunState.live.value = null
        RunState.gps.value = GpsStatus()
        if (summary == null) {
            RunState.notice.value = "距離が短かったため、記録に残しませんでした"
            stopSelf()
            return
        }
        notify(Notifications.status(this, "記録を保存しています", "地図の画像を作っています", paused = null))
        scope.launch {
            container.repository.refresh()
            RunState.finishedId.value = summary.id
            finishUp(summary)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** 記録の後始末。出発地の名前を調べ、地図の画像を作って、完了を通知する */
    private suspend fun finishUp(summary: RunSummary) {
        var current = summary
        val place = container.places.name(summary.startLat, summary.startLon)
        if (place != null) {
            current = current.copy(startLabel = place)
            container.repository.save(current)
        }
        container.images.ensure(current, container.repository.track(summary.id), settings.mapStyleDark)
        Notifications.finished(this, current)
    }

    override fun onPoint(point: TrackPoint) {
        if (!recorder.isActive) return
        recorder.onPoint(point)
        publish()
    }

    override fun onStatus(status: GpsStatus) {
        RunState.gps.value = status
    }

    private fun publish() {
        RunState.live.value = recorder.live()
    }

    private fun refreshNotification() {
        val live = recorder.live() ?: return
        val now = System.currentTimeMillis()
        val parts = buildList {
            add(Format.distance(live.distanceM))
            add(Format.clock(live.activeMs(now)))
            live.paceSecPerKm?.takeIf { !live.paused }?.let { add(Format.pace(it)) }
        }
        notify(Notifications.status(this, if (live.paused) "一時停止中" else "ランを計測中", parts.joinToString(" ・ "), live.paused))
    }

    private fun notify(n: android.app.Notification) {
        getSystemService(NotificationManager::class.java).notify(Notifications.ID_STATUS, n)
    }

    /** サービスを前面に出す。位置情報の権限が無いと、種類が location の前面サービスは始められない */
    private fun enterForeground(): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            RunState.notice.value = "位置情報の権限が必要です"
            return false
        }
        return try {
            val n = Notifications.status(this, "ランを計測中", "測位を待っています", paused = false)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notifications.ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(Notifications.ID_STATUS, n)
            }
            true
        } catch (e: Exception) {
            RunState.notice.value = "バックグラウンドで動かせませんでした: ${e.message}"
            false
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "runtracker:recording").apply { acquire(12 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        gps.stop()
        // サービスが終わるときに計測中なら、そこまでを残す(点はすでにファイルに書かれている)
        if (recorder.isActive) {
            val startMs = recorder.live()?.startTimeMs ?: System.currentTimeMillis()
            recorder.finish(settings.minDistanceM, container.body.weightAt(startMs))
        }
        releaseWakeLock()
        RunState.live.value = null
        RunState.serviceRunning.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ttech.runtracker.START"
        const val ACTION_PAUSE = "com.ttech.runtracker.PAUSE"
        const val ACTION_RESUME = "com.ttech.runtracker.RESUME"
        const val ACTION_STOP = "com.ttech.runtracker.STOP"
        private const val TICK_MS = 5_000L

        /** 計測を始める(前面のサービスとして) */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RunService::class.java).setAction(ACTION_START))
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)

        fun resume(context: Context) = send(context, ACTION_RESUME)

        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, RunService::class.java).setAction(action))
        }
    }
}
