package com.ttech.stretchreminder.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 次のストレッチの時刻に通知を出すよう AlarmManager に予約する。
 * 正確なアラーム(SCHEDULE_EXACT_ALARM 権限が必要)ではなく、数分のずれを許す通常のアラームを使う。
 * 同じ PendingIntent で予約し直すので、予約は常に1件だけになる。
 */
object StretchReminderScheduler {
    fun schedule(context: Context, atMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val trigger = maxOf(atMillis, System.currentTimeMillis() + 1_000)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, StretchReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
