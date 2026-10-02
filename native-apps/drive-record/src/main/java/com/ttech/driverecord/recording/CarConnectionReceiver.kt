package com.ttech.driverecord.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import com.ttech.driverecord.DriveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android Auto の接続状況が変わったとき、OSから(このアプリが起動していなくても)届く通知。
 * 待機のサービス([DriveService])が、何らかの理由で既に終わっていても、これをきっかけに
 * 立ち上げ直す。[BootReceiver](端末の再起動時)と同じ考え方で、Android Auto への接続そのものを
 * きっかけにする版。
 *
 * ただし、ユーザーが「強制停止」した直後はAndroidの仕組み上この通知も届かない(次に一度手で
 * アプリを開くまで、どの通知も届かないようになっている)。これはAndroidの仕様で、アプリ側では
 * 回避できない。
 */
class CarConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CarConnection.ACTION_CAR_CONNECTION_UPDATED) return
        val pending = goAsync()
        val appContext = context.applicationContext

        // CarConnection の LiveData はメインスレッドでしか観測できないので、そちらで1回だけ値を受け取る
        Handler(Looper.getMainLooper()).post {
            val connection = CarConnection(appContext)
            lateinit var observer: Observer<Int>
            observer = Observer { type ->
                connection.type.removeObserver(observer)
                if (type == CarConnection.CONNECTION_TYPE_PROJECTION) {
                    val container = (appContext as DriveApplication).container
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            if (container.settings.settings.first().autoRecord) DriveService.ensureRunning(appContext)
                        } finally {
                            pending.finish()
                        }
                    }
                } else {
                    pending.finish()
                }
            }
            connection.type.observeForever(observer)
        }
    }
}
