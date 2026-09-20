package com.kawamonn.store.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kawamonn.store.MainActivity
import com.kawamonn.store.R
import com.kawamonn.store.StoreApp
import java.util.concurrent.TimeUnit

/** 定期的にカタログを確認し、更新があれば通知する */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as StoreApp).container
        val index = try {
            container.api.index().items
        } catch (_: Exception) {
            return Result.retry()
        }
        val updates = UpdateDetector.detect(index) { container.installedApps.versionCode(it) }
        if (updates.isEmpty()) {
            container.updatePrefs.clear()
            return Result.success()
        }

        // 同じ内容を毎回通知しないよう、通知済みの (package:versionCode) を覚えておく
        val keys = updates.map { "${it.entry.packageName}:${it.entry.latest.versionCode}" }.toSet()
        if (keys == container.updatePrefs.notifiedKeys()) return Result.success()
        container.updatePrefs.save(keys)

        notify(applicationContext, updates)
        return Result.success()
    }

    private fun notify(context: Context, updates: List<AvailableUpdate>) {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, "updates")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val names = updates.joinToString("、") { it.entry.name }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${updates.size}件のアップデートがあります")
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "updates"
        private const val NOTIFICATION_ID = 1
        private const val WORK_NAME = "update-check"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val channel = NotificationChannel(CHANNEL_ID, "アップデート通知", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** 通知済みの更新を覚えておく小さなストア */
class UpdatePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    fun notifiedKeys(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()
    fun save(keys: Set<String>) = prefs.edit().putStringSet(KEY, keys).apply()
    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "notified"
    }
}
