package com.kawamonn.store.util

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

/** ダウンロード数の表示用。1万以上は「1.2万」形式 */
fun formatCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 10_000 -> String.format(Locale.US, "%,d", count)
    else -> String.format(Locale.US, "%.1f万", count / 10_000.0).replace(".0万", "万")
}

/** "android.permission.CAMERA" → "CAMERA" */
fun shortPermission(name: String): String = name.removePrefix("android.permission.")
