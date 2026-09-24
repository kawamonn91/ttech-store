package com.ttech.cycletracker.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class CycleEntry(
    val id: String,
    /** 生理開始日。ISO8601 (YYYY-MM-DD)。 */
    val startDate: String,
)

/** 記録一覧の表示順(新しい開始日が先頭)。 */
fun List<CycleEntry>.sortedByStartDateDescending(): List<CycleEntry> = sortedByDescending { it.startDate }

/**
 * 実測の平均周期(日)。記録が2件未満なら設定値の [fallback] をそのまま使う
 * (Webアプリ版と同じロジック: 隣接する開始日の差の平均を四捨五入する)。
 */
fun averageCycleLength(sortedDescending: List<CycleEntry>, fallback: Int): Int {
    if (sortedDescending.size < 2) return fallback
    val diffs = sortedDescending.zipWithNext { newer, older ->
        ChronoUnit.DAYS.between(LocalDate.parse(older.startDate), LocalDate.parse(newer.startDate))
    }
    return Math.round(diffs.average()).toInt()
}

/** 次回の生理開始予測日。 */
fun predictNextStart(lastStart: String, avgCycleDays: Int): String =
    LocalDate.parse(lastStart).plusDays(avgCycleDays.toLong()).toString()

data class FertileWindow(val start: String, val end: String)

/** 排卵日周辺の目安期間(次回開始予測の18日前〜11日前)。 */
fun fertileWindow(lastStart: String, avgCycleDays: Int): FertileWindow {
    val base = LocalDate.parse(lastStart)
    return FertileWindow(
        start = base.plusDays((avgCycleDays - 18).toLong()).toString(),
        end = base.plusDays((avgCycleDays - 11).toLong()).toString(),
    )
}
