package com.ttech.stretchreminder.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

private const val REQUEST_CODE = 1

/**
 * 次のストレッチ時刻に通知を出すアラームを1つだけ予約する。
 * 正確な時刻指定(SCHEDULE_EXACT_ALARM)は不要な、多少の誤差を許すアラームを使う。
 * すでに時刻を過ぎている場合は予約しない(アプリを開くたびに通知が出るのを防ぐ)。
 */
fun scheduleReminder(context: Context, triggerAtMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = reminderPendingIntent(context)
    alarmManager.cancel(pendingIntent)
    if (triggerAtMillis <= nowMillis) return
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
}

private fun reminderPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, ReminderReceiver::class.java)
    return PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
