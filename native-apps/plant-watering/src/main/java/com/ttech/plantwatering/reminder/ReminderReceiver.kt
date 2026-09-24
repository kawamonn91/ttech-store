package com.ttech.plantwatering.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ttech.plantwatering.MainActivity
import com.ttech.plantwatering.R
import com.ttech.plantwatering.data.PlantStore
import com.ttech.plantwatering.domain.dueNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val CHANNEL_ID = "plant_watering"
private const val NOTIFICATION_ID = 1

/** 毎朝、水やりの時期の植物があれば通知し、翌朝のアラームを予約し直す。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val names = PlantStore(appContext).current().dueNames(LocalDate.now())
                if (names.isNotEmpty()) notifyDue(appContext, names)
                scheduleDailyCheck(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}

private fun notifyDue(context: Context, names: List<String>) {
    val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (!canPost) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "水やりのお知らせ", NotificationManager.IMPORTANCE_DEFAULT),
    )
    val openApp = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("水やりの時期です")
        .setContentText(names.joinToString("、"))
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}
