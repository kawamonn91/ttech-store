package com.ttech.runtracker.recording

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
import com.ttech.runtracker.MainActivity
import com.ttech.runtracker.R
import com.ttech.runtracker.domain.RunSummary
import com.ttech.track.domain.Format

/** 通知の作成。計測中の通知と、記録が終わったときの通知 */
object Notifications {
    const val CHANNEL_STATUS = "run_status"
    const val CHANNEL_RESULT = "run_result"
    const val ID_STATUS = 2001
    private const val ID_RESULT = 2002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "計測中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "ランを計測しているときに、距離・時間・ペースを表示します"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "記録の完了", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "ランの記録が終わったときにお知らせします"
            },
        )
    }

    private fun openApp(context: Context, runId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            runId?.let { putExtra(MainActivity.EXTRA_RUN_ID, it) }
        }
        return PendingIntent.getActivity(context, runId?.hashCode() ?: 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(context: Context, code: Int, action: String): PendingIntent =
        PendingIntent.getService(
            context, code, Intent(context, RunService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 計測中の通知。[paused] のときは「再開」、そうでなければ「一時停止」のボタンを出す */
    fun status(context: Context, title: String, text: String, paused: Boolean?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (paused != null) {
            if (paused) builder.addAction(0, "再開", action(context, 1, RunService.ACTION_RESUME))
            else builder.addAction(0, "一時停止", action(context, 2, RunService.ACTION_PAUSE))
            builder.addAction(0, "終了", action(context, 3, RunService.ACTION_STOP))
        }
        return builder.build()
    }

    fun finished(context: Context, summary: RunSummary) {
        if (permissionDenied(context)) return
        val text = "${Format.distance(summary.distanceM)} ・ ${Format.duration(summary.movingMs)} ・ ${Format.pace(summary.avgPaceSecPerKm)}"
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle("ランを記録しました")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, summary.id))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_RESULT, n)
    }

    /** 通知の権限(Android 13以降)が無ければ、通知は出さない(記録には影響しない) */
    private fun permissionDenied(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
