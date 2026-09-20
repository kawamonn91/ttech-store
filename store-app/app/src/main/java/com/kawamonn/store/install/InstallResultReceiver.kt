package com.kawamonn.store.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.kawamonn.store.StoreApp

/** PackageInstaller セッションの結果を受け取り、アプリ内のイベントに変換する */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val events = (context.applicationContext as StoreApp).container.installEvents

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // システムのインストール確認画面を出す
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            events.tryEmit(InstallEvent.AwaitingUser(packageName))
            return
        }
        events.tryEmit(InstallEvent.Finished(packageName, status, message))
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "com.kawamonn.store.TARGET_PACKAGE"
    }
}

sealed interface InstallEvent {
    val packageName: String

    data class AwaitingUser(override val packageName: String) : InstallEvent
    data class Finished(override val packageName: String, val status: Int, val message: String?) : InstallEvent
}
