package com.ttech.driverecord.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ttech.driverecord.recording.DriveService

/**
 * 開発用。Android Auto の接続・切断を、adb で模擬する。
 *   adb shell am broadcast -n <パッケージ>/com.ttech.driverecord.debug.DebugReceiver -a com.ttech.driverecord.DEBUG_CAR --ez connected true
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DriveService.ACTION_DEBUG_CAR) return
        val service = Intent(context, DriveService::class.java)
            .setAction(DriveService.ACTION_DEBUG_CAR)
            .putExtra(DriveService.EXTRA_CONNECTED, intent.getBooleanExtra(DriveService.EXTRA_CONNECTED, false))
        ContextCompat.startForegroundService(context, service)
    }
}
