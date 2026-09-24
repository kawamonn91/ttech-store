package com.ttech.plantwatering.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ttech.plantwatering.domain.nextReminderTime
import java.time.ZonedDateTime

private const val REQUEST_CODE = 1

/**
 * 次の毎朝9時に、水やりの時期の植物があるか確認して通知するアラームを1つだけ予約する。
 * 正確な時刻指定(SCHEDULE_EXACT_ALARM)は不要な、多少の誤差を許すアラームを使う。
 */
fun scheduleDailyCheck(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarmManager.cancel(pendingIntent)
    alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        nextReminderTime(now).toInstant().toEpochMilli(),
        pendingIntent,
    )
}
