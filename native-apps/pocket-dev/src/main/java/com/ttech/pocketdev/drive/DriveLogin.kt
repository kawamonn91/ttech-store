package com.ttech.pocketdev.drive

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.thread

/** アプリ全体で1つの rclone（名前解決の中継と rcd も共有する） */
object Drive {
    /** 誰も使わなくなってから rclone rcd を止めるまでの時間（フォルダ選択 → IDE の切り替えで起動し直さない長さ） */
    private const val DAEMON_IDLE_MS = 30_000L

    private val proxy by lazy { ConnectProxy() }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var instance: Rclone? = null

    @Volatile
    private var sharedDaemon: SharedCloseable<RcloneDaemon>? = null

    fun rclone(context: Context): Rclone =
        instance ?: synchronized(this) {
            instance ?: Rclone(context.applicationContext, proxy).also { instance = it }
        }

    /** 共有の rclone rcd を借りる。使い終わったら閉じる（起動は [RcloneDaemon.ensureStarted]） */
    fun acquireDaemon(context: Context): Lease<RcloneDaemon> =
        (sharedDaemon ?: synchronized(this) {
            sharedDaemon ?: SharedCloseable(
                create = { RcloneDaemon(rclone(context)) },
                idleMs = DAEMON_IDLE_MS,
                schedule = { delayMs, action ->
                    val runnable = Runnable(action)
                    mainHandler.postDelayed(runnable, delayMs)
                    ({ mainHandler.removeCallbacks(runnable) })
                },
                runInBackground = { action -> thread(name = "rclone-rcd-stop", isDaemon = true) { action() } },
            ).also { sharedDaemon = it }
        }).acquire()

    /** rclone rcd をすぐに止める（ログインの情報が変わったとき）。待たないのでメインスレッドから呼んでよい */
    fun stopDaemon() {
        sharedDaemon?.stopNow()
    }
}

/**
 * Google のログインの進み具合。ログインそのものは [DriveLoginService] の中で動かす
 * （ブラウザに切り替わっている間にアプリが止められると、rclone が同意の結果を受け取れないため）。
 */
object DriveLogin {

    data class State(
        val running: Boolean = false,
        /** ブラウザで開く同意画面の URL（画面が開いたら [urlOpened] で消す） */
        val authUrl: String? = null,
        val error: String? = null,
        /** ログインが終わるたびに増える（画面がログインの状態を読み直す合図） */
        val completed: Int = 0,
    )

    private const val TAG = "DriveLogin"
    private const val TIMEOUT_MS = 5 * 60 * 1000L

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    @Volatile
    internal var job: Job? = null

    fun start(context: Context) {
        _state.update { it.copy(running = true, authUrl = null, error = null) }
        val app = context.applicationContext
        try {
            ContextCompat.startForegroundService(app, Intent(app, DriveLoginService::class.java))
        } catch (e: Exception) {
            // 画面を出していないときなど、常駐サービスを始められないことがある
            Log.w(TAG, "ログイン用のサービスを始められません", e)
            _state.update { it.copy(running = false, error = "ログインを始められませんでした。もう一度押してください") }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(running = false, authUrl = null) }
    }

    fun urlOpened() {
        _state.update { it.copy(authUrl = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** [DriveLoginService] から呼ぶ */
    internal suspend fun run(context: Context) {
        _state.update { it.copy(running = true, authUrl = null, error = null) }
        try {
            withTimeout(TIMEOUT_MS) {
                Drive.rclone(context).login(
                    onUrl = { url -> _state.update { it.copy(authUrl = url) } },
                    log = { Log.i(TAG, it) },
                )
            }
            Drive.stopDaemon()
            _state.update { State(completed = it.completed + 1) }
        } catch (e: TimeoutCancellationException) {
            _state.update { State(error = "5 分のうちに許可が終わらなかったため、ログインをやめました", completed = it.completed) }
        } catch (e: CancellationException) {
            _state.update { State(completed = it.completed) }
            throw e
        } catch (e: Exception) {
            _state.update { State(error = e.message ?: "ログインできませんでした", completed = it.completed) }
        }
    }
}
