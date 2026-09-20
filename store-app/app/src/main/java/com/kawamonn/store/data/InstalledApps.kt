package com.kawamonn.store.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat

/** 端末のインストール状況を調べる */
class InstalledApps(private val context: Context) {
    private val pm: PackageManager get() = context.packageManager

    /** インストール済みなら versionCode、未インストールなら null */
    fun versionCode(packageName: String): Long? = try {
        PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(packageName, 0))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)
}
