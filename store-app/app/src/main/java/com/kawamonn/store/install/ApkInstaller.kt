package com.kawamonn.store.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/** 検証済みの APK を PackageInstaller セッションで端末にインストールする */
class ApkInstaller(private val context: Context) {

    /** セッションをコミットする。結果は [InstallResultReceiver] 経由で非同期に届く */
    fun install(apk: File, packageName: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                // 更新時など条件を満たす場合、確認画面を省略できる(満たさなければ通常の確認画面)
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                // このストアを更新の担当として登録し、他ストアによる勝手な更新を防ぐ
                setRequestUpdateOwnership(true)
            }
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val resultIntent = Intent(context, InstallResultReceiver::class.java)
                .putExtra(InstallResultReceiver.EXTRA_TARGET_PACKAGE, packageName)
            // システムが結果を書き込むため MUTABLE が必要
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}
