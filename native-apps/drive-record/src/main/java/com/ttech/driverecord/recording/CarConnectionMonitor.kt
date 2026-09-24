package com.ttech.driverecord.recording

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/**
 * 端末が Android Auto につながっているかを監視する(Android Auto の公式の仕組み CarConnection を使う)。
 * つながっている(スマホの画面を車に映している)ときに [onChange] へ true、切れたら false を渡す。
 * メインスレッドで start / stop すること。
 */
class CarConnectionMonitor(context: Context, private val onChange: (connected: Boolean) -> Unit) {
    private val connection = CarConnection(context.applicationContext)
    private val observer = Observer<Int> { type -> onChange(type == CarConnection.CONNECTION_TYPE_PROJECTION) }
    private var started = false

    fun start() {
        if (started) return
        started = true
        connection.type.observeForever(observer)
    }

    fun stop() {
        if (!started) return
        started = false
        connection.type.removeObserver(observer)
    }
}
