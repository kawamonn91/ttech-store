package com.ttech.driverecord.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ttech.driverecord.MainActivity
import com.ttech.driverecord.R
import com.ttech.driverecord.domain.DriveSummary
import com.ttech.track.domain.Format

/** 通知の作成。常駐の通知(待機中・記録中)と、記録が終わったときの通知 */
object Notifications {
    const val CHANNEL_STATUS = "drive_status"
    const val CHANNEL_RESULT = "drive_result"
    const val ID_STATUS = 1001
    private const val ID_RESULT = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "待機・記録中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Android Auto の接続を待っているとき、ドライブを記録しているときに表示します"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "記録の完了", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "ドライブの記録が終わったときにお知らせします"
            },
        )
    }

    private fun openApp(context: Context, driveId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            driveId?.let { putExtra(MainActivity.EXTRA_DRIVE_ID, it) }
        }
        return PendingIntent.getActivity(context, driveId?.hashCode() ?: 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun status(context: Context, text: String, recording: Boolean): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle(if (recording) "ドライブを記録中" else "Android Auto の接続を待っています")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (recording) {
            val stop = PendingIntent.getService(
                context, 1, Intent(context, DriveService::class.java).setAction(DriveService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "記録を終える", stop)
        }
        return builder.build()
    }

    fun finished(context: Context, summary: DriveSummary) {
        if (Build_permissionDenied(context)) return
        val text = "${Format.distance(summary.distanceM)} ・ ${Format.duration(summary.durationMs)} ・ 最高 ${Format.speedKmh(summary.maxSpeedMps)}"
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle("ドライブを記録しました")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, summary.id))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_RESULT, n)
    }

    /** 通知の権限(Android 13以降)が無ければ、通知は出さない(記録には影響しない) */
    @Suppress("FunctionName")
    private fun Build_permissionDenied(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
