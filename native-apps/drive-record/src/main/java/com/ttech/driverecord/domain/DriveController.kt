package com.ttech.driverecord.domain

/** 記録の開始・終了を決める側の指示 */
sealed interface DriveCommand {
    data class Start(val trigger: String) : DriveCommand
    data object Stop : DriveCommand
}

/** ユーザーが変えられる設定 */
data class DriveSettings(
    /** Android Auto につながったら、自動で記録を始める */
    val autoRecord: Boolean = true,
    /**
     * Android Auto との接続が切れてから、記録を終えるまで待つ秒数(既定は10秒)。
     * 接続が一瞬途切れても(無線接続など)、その間に接続し直せば、別々の記録に分かれずに同じ記録を続けられる。
     * 長め(数十分)にすると、サービスエリアなどでの休憩の間もGPSを受信し続け、休憩をまたいで
     * 同じドライブの記録を続けられる(休憩は「停止」として記録に残る)。0 にすると、切れた瞬間に終える。
     */
    val disconnectGraceSec: Int = 10,
    /** これより短い(m)ドライブは、記録に残さない(駐車場での出し入れなど) */
    val minDistanceM: Int = 300,
    val mapStyleDark: Boolean = true,
    /** Android Auto につながったら、このアプリの画面を自動で開く */
    val autoLaunchApp: Boolean = true,
) {
    companion object {
        /** 一瞬の途切れ用の短い値と、休憩に対応するための長い値の両方を選べるようにする */
        val GRACE_CHOICES = listOf(10, 30, 60, 120, 300, 900, 1800, 3600)
        val MIN_DISTANCE_CHOICES = listOf(0, 100, 300, 500, 1000)
    }
}

/**
 * Android Auto の接続状態と、手動の操作から、記録を始める・終える指示を出す。
 * 時刻は呼び出し側から受け取る(時計に依存しないので、テストで再現できる)。
 *
 * ルール:
 *  - 自動記録がオンで Android Auto につながったら、記録を始める
 *  - 切れたら、待機時間([DriveSettings.disconnectGraceSec]。既定は10秒)が過ぎてから記録を終える。その間に再接続すれば、そのまま続ける
 *    (待機時間が0なら、切れた瞬間に終える)
 *  - 手動で始めた記録は、Android Auto の接続とは関係なく、手動で止めるまで続ける
 *  - 自動で始めた記録を手動で止めたら、次に接続し直すまでは、自動で始めない
 */
class DriveController(private var settings: DriveSettings = DriveSettings()) {
    private enum class Mode { Idle, Auto, Manual }

    private var mode = Mode.Idle
    private var carConnected = false
    private var stopAtMs: Long? = null
    private var suppressUntilDisconnect = false

    val isRecording: Boolean get() = mode != Mode.Idle

    /** 待機時間が過ぎる時刻。これ以降に [onTick] を呼ぶと記録を終える。無ければ null */
    val pendingStopAtMs: Long? get() = stopAtMs

    fun updateSettings(new: DriveSettings, nowMs: Long): DriveCommand? {
        settings = new
        // 自動記録をオフにしたら、自動で始めた記録は、待機時間を待たずに終える
        if (!new.autoRecord && mode == Mode.Auto) return stop()
        // オンにしたときに、すでに接続していれば始める
        return maybeStartAuto(nowMs)
    }

    fun onCarConnection(connected: Boolean, nowMs: Long): DriveCommand? {
        val was = carConnected
        carConnected = connected
        if (connected == was) return null
        return if (connected) {
            stopAtMs = null // 待機中に再接続したら、終えずに続ける
            maybeStartAuto(nowMs)
        } else {
            suppressUntilDisconnect = false
            if (mode != Mode.Auto) {
                null
            } else if (settings.disconnectGraceSec <= 0) {
                // 待たずに、切れた瞬間に終える(定期の見張り [onTick] の周期を待たない)
                stop()
            } else {
                stopAtMs = nowMs + settings.disconnectGraceSec * 1000L
                null
            }
        }
    }

    fun onTick(nowMs: Long): DriveCommand? {
        val at = stopAtMs ?: return null
        return if (mode == Mode.Auto && nowMs >= at) stop() else null
    }

    fun manualStart(): DriveCommand? {
        if (mode != Mode.Idle) return null
        mode = Mode.Manual
        stopAtMs = null
        return DriveCommand.Start(Trigger.MANUAL)
    }

    fun manualStop(): DriveCommand? {
        if (mode == Mode.Idle) return null
        if (mode == Mode.Auto && carConnected) suppressUntilDisconnect = true
        return stop()
    }

    /** サービスが再起動したときなど、すでに記録中の状態に合わせる */
    fun restore(trigger: String) {
        mode = if (trigger == Trigger.ANDROID_AUTO) Mode.Auto else Mode.Manual
    }

    private fun maybeStartAuto(nowMs: Long): DriveCommand? {
        if (!settings.autoRecord || !carConnected || mode != Mode.Idle || suppressUntilDisconnect) return null
        mode = Mode.Auto
        stopAtMs = null
        return DriveCommand.Start(Trigger.ANDROID_AUTO)
    }

    private fun stop(): DriveCommand {
        mode = Mode.Idle
        stopAtMs = null
        return DriveCommand.Stop
    }
}
