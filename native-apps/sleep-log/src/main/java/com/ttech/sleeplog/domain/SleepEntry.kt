package com.ttech.sleeplog.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class SleepEntry(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    /** "HH:mm" */
    val bedTime: String,
    /** "HH:mm" */
    val wakeTime: String,
    val durationHours: Double,
    /** 睡眠の質 1〜5 */
    val quality: Int,
)

/**
 * 就寝〜起床の睡眠時間(時間、小数第1位に丸め)。起床が就寝以前の時刻なら日をまたいだとみなす
 * (同じ時刻なら24時間)。Webアプリ版と同じ計算。
 */
fun calcDurationHours(bedTime: String, wakeTime: String): Double {
    var minutes = toMinutes(wakeTime) - toMinutes(bedTime)
    if (minutes <= 0) minutes += 24 * 60
    return (minutes / 60.0 * 10).roundToInt() / 10.0
}

private fun toMinutes(time: String): Int {
    val parts = time.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h * 60 + m
}

/** 平均睡眠時間(小数第1位に丸め)。記録がなければnull。 */
fun averageHours(entries: List<SleepEntry>): Double? {
    if (entries.isEmpty()) return null
    return (entries.sumOf { it.durationHours } / entries.size * 10).roundToInt() / 10.0
}

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** JSの数値表示と同じく、整数なら小数点以下を付けない(7.0 → "7"、7.5 → "7.5")。 */
fun formatHours(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
