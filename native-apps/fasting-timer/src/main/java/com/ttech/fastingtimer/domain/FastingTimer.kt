package com.ttech.fastingtimer.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class FastSession(
    /** epoch ms */
    val startedAt: Long,
    val goalHours: Int,
)

/** "HH:mm:ss" 形式の経過時間表示(Webアプリ版と同じロジック)。 */
fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** 目標に対する達成率(%)。上限999%でカンストする(Webアプリ版と同じ)。 */
fun progressPercent(elapsedMs: Long, goalHours: Int): Int {
    if (goalHours <= 0) return 0
    val goalMs = goalHours * 60 * 60 * 1000L
    return ((elapsedMs.toDouble() / goalMs) * 100).roundToInt().coerceAtMost(999)
}

/** 経過時間を時間単位(小数第1位まで)に変換する。 */
fun hoursElapsed(elapsedMs: Long): Double = ((elapsedMs / 3_600_000.0) * 10).roundToInt() / 10.0

/** 記録一覧の先頭に追加し、最大件数で切り詰める(Webアプリ版と同じ、最新20件)。 */
fun List<Double>.pushHistory(hours: Double, maxSize: Int = 20): List<Double> = (listOf(hours) + this).take(maxSize)
