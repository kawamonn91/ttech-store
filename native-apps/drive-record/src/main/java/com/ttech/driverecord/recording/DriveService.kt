package com.ttech.driverecord.recording

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
import com.ttech.driverecord.BuildConfig
import com.ttech.driverecord.DriveApplication
import com.ttech.driverecord.DriveContainer
import com.ttech.driverecord.DriveState
import com.ttech.driverecord.domain.ConnectionEdge
import com.ttech.driverecord.domain.DriveCommand
import com.ttech.driverecord.domain.DriveController
import com.ttech.driverecord.domain.DriveRecorder
import com.ttech.driverecord.domain.DriveSettings
import com.ttech.driverecord.domain.DriveSummary
import com.ttech.driverecord.domain.Trigger
import com.ttech.track.domain.Format
import com.ttech.track.domain.TrackFilter
import com.ttech.track.domain.TrackPoint
import com.ttech.track.location.GpsStatus
import com.ttech.track.location.GpsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * このアプリの心臓部。
 *  - 待機: Android Auto の接続を見張る(小さな常駐の通知が出る)
 *  - 記録: つながったら(または手動で始めたら)GPSを1秒ごとに受け取って、ファイルに記録する
 *  - 終了: 切れて待機時間が過ぎたら(または手動で止めたら)、統計を計算し、地名と地図の画像を作る
 */
class DriveService : Service(), GpsTracker.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: DriveContainer
    private lateinit var controller: DriveController
    private lateinit var recorder: DriveRecorder
    private lateinit var gps: GpsTracker
    private var carMonitor: CarConnectionMonitor? = null
    private val launchEdge = ConnectionEdge()
    private var wakeLock: PowerManager.WakeLock? = null
    private var settings = DriveSettings()
    private var settingsJob: Job? = null
    private var lastNotifyMs = 0L

    private val ticker = object : Runnable {
        override fun run() {
            controller.onTick(System.currentTimeMillis())?.let(::execute)
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as DriveApplication).container
        Notifications.ensureChannels(this)
        controller = DriveController()
        recorder = DriveRecorder(container.files, TrackFilter())
        gps = GpsTracker(this, intervalMs = 1000L)
        if (!enterForeground("待機中")) {
            stopSelf()
            return
        }
        DriveState.serviceRunning.value = true

        // 前回、記録の途中でアプリが終了していたら、点から復旧する
        scope.launch {
            withContext(Dispatchers.IO) {
                val min = container.settings.settings.first().minDistanceM
                DriveRecorder.recover(container.files, min)
            }
            container.repository.refresh()
        }

        settingsJob = scope.launch {
            container.settings.settings.collect { s ->
                settings = s
                controller.updateSettings(s, System.currentTimeMillis())?.let(::execute)
                // 自動記録をオフにしていて、記録もしていなければ、待機をやめる
                if (!s.autoRecord && !controller.isRecording) stopSelf()
            }
        }
        carMonitor = CarConnectionMonitor(this) { connected ->
            DriveState.carConnected.value = connected
            if (launchEdge.onConnected(connected) && settings.autoLaunchApp) {
                Notifications.launchApp(this)
            }
            controller.onCarConnection(connected, System.currentTimeMillis())?.let(::execute)
        }.also { it.start() }
        handler.postDelayed(ticker, TICK_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MANUAL -> controller.manualStart()?.let(::execute)
            ACTION_STOP -> controller.manualStop()?.let(::execute)
            // 開発用: Android Auto の接続を模擬する(デバッグビルドだけ)
            ACTION_DEBUG_CAR -> if (BuildConfig.DEBUG) {
                val connected = intent.getBooleanExtra(EXTRA_CONNECTED, false)
                DriveState.carConnected.value = connected
                if (launchEdge.onConnected(connected) && settings.autoLaunchApp) {
                    Notifications.launchApp(this)
                }
                controller.onCarConnection(connected, System.currentTimeMillis())?.let(::execute)
            }
        }
        return START_STICKY
    }

    private fun execute(command: DriveCommand) {
        when (command) {
            is DriveCommand.Start -> startRecording(command.trigger)
            DriveCommand.Stop -> stopRecording()
            is DriveCommand.BreakRecorded -> recorder.addBreak(command.startMs, command.endMs)
        }
    }

    private fun startRecording(trigger: String) {
        recorder.start(trigger)
        val ok = gps.start(this)
        if (!ok) {
            DriveState.notice.value = "GPSを使えません。端末の位置情報をオンにして、権限を確認してください"
            recorder.finish(minDistanceM = Int.MAX_VALUE)
            controller.manualStop()
            update("GPSを使えません")
            return
        }
        DriveState.notice.value = null
        acquireWakeLock()
        publish()
        update("測位を待っています")
    }

    private fun stopRecording() {
        gps.stop()
        releaseWakeLock()
        val summary = recorder.finish(settings.minDistanceM)
        DriveState.live.value = null
        DriveState.gps.value = GpsStatus()
        update("待機中")
        if (summary != null) {
            scope.launch {
                container.repository.refresh()
                finishUp(summary)
            }
        }
        // 自動記録をオフにしていて、記録も終わったら、待機をやめる
        if (!settings.autoRecord && !controller.isRecording) {
            stopSelf()
        }
    }

    /** 記録の後始末。出発地・到着地の名前を調べ、地図の画像を作って、完了を通知する */
    private suspend fun finishUp(summary: DriveSummary) {
        var current = summary
        val start = container.places.name(summary.startLat, summary.startLon)
        val end = container.places.name(summary.endLat, summary.endLon)
        if (start != null || end != null) {
            current = current.copy(startLabel = start, endLabel = end)
            container.repository.save(current)
        }
        val points = container.repository.track(summary.id)
        container.images.ensure(current, points, settings.mapStyleDark)
        Notifications.finished(this, current)
    }

    override fun onPoint(point: TrackPoint) {
        if (!recorder.isActive) return
        recorder.onPoint(point)
        publish()
        val now = System.currentTimeMillis()
        if (now - lastNotifyMs >= NOTIFY_INTERVAL_MS) {
            lastNotifyMs = now
            recorder.live()?.let { update("${Format.distance(it.distanceM)} ・ ${Format.clock(it.elapsedMs(now))}") }
        }
    }

    override fun onStatus(status: GpsStatus) {
        DriveState.gps.value = status
    }

    private fun publish() {
        DriveState.live.value = recorder.live()
    }

    private fun update(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(Notifications.ID_STATUS, Notifications.status(this, text, recorder.isActive))
    }

    /** サービスを前面に出す。位置情報の権限が無いと、種類が location の前面サービスは始められない */
    private fun enterForeground(text: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            DriveState.notice.value = "位置情報の権限が必要です"
            return false
        }
        return try {
            val n = Notifications.status(this, text, recording = false)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notifications.ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(Notifications.ID_STATUS, n)
            }
            true
        } catch (e: Exception) {
            DriveState.notice.value = "バックグラウンドで動かせませんでした: ${e.message}"
            false
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "driverecord:recording").apply { acquire(12 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        carMonitor?.stop()
        gps.stop()
        // サービスが終わるときに記録中なら、そこまでを残す(点はすでにファイルに書かれている)
        if (recorder.isActive) recorder.finish(settings.minDistanceM)
        releaseWakeLock()
        DriveState.live.value = null
        DriveState.serviceRunning.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_MANUAL = "com.ttech.driverecord.START_MANUAL"
        const val ACTION_STOP = "com.ttech.driverecord.STOP"
        const val ACTION_DEBUG_CAR = "com.ttech.driverecord.DEBUG_CAR"
        const val EXTRA_CONNECTED = "connected"
        /** 待機時間(既定10秒)が過ぎたらすぐ終えられるよう、1秒ごとに確かめる */
        private const val TICK_MS = 1_000L
        private const val NOTIFY_INTERVAL_MS = 10_000L

        /** 待機のサービスが動いていなければ始める */
        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DriveService::class.java))
        }

        fun startManual(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DriveService::class.java).setAction(ACTION_START_MANUAL))
        }

        fun stopRecording(context: Context) {
            context.startService(Intent(context, DriveService::class.java).setAction(ACTION_STOP))
        }

        /** 待機のサービスをやめる(自動記録をオフにしたとき) */
        fun shutdown(context: Context) {
            context.stopService(Intent(context, DriveService::class.java))
        }
    }
}
