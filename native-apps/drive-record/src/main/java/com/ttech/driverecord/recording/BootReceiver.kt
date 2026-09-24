package com.ttech.driverecord.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ttech.driverecord.DriveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 端末の再起動・アプリの更新のあとも、Android Auto の待機を続ける */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val container = (context.applicationContext as DriveApplication).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (container.settings.settings.first().autoRecord) DriveService.ensureRunning(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
