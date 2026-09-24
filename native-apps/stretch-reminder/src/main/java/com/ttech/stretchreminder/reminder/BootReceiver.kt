package com.ttech.stretchreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ttech.stretchreminder.data.StretchStore
import com.ttech.stretchreminder.domain.nextReminderAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 端末の再起動でアラームは消えるため、起動時に次のリマインドを予約し直す。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = StretchStore(appContext).current()
                scheduleReminder(appContext, nextReminderAt(state))
            } finally {
                pending.finish()
            }
        }
    }
}
