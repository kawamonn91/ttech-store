package com.ttech.plantwatering.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 端末の再起動でアラームは消えるため、起動時に毎朝の確認を予約し直す。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) scheduleDailyCheck(context.applicationContext)
    }
}
