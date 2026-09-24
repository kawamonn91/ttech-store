package com.ttech.watertracker.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** ワンタップで記録できる量(Webアプリ版と同じ)。 */
val QUICK_AMOUNTS_ML = listOf(100, 200, 350, 500)
const val DEFAULT_GOAL_ML = 2000

@Serializable
data class DailyLog(
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val totalMl: Int,
)

/** [date] の合計に [ml] を足す。その日の記録がなければ先頭に作る。合計はマイナスにならない。 */
fun List<DailyLog>.addAmount(date: String, ml: Int): List<DailyLog> =
    if (any { it.date == date }) {
        map { if (it.date == date) it.copy(totalMl = maxOf(it.totalMl + ml, 0)) else it }
    } else {
        listOf(DailyLog(date, maxOf(ml, 0))) + this
    }

fun List<DailyLog>.totalOn(date: String): Int = firstOrNull { it.date == date }?.totalMl ?: 0

fun List<DailyLog>.resetDay(date: String): List<DailyLog> = filterNot { it.date == date }

/** 今日以外の記録を新しい順に最大 [limit] 件。 */
fun List<DailyLog>.recentExcluding(date: String, limit: Int = 7): List<DailyLog> =
    filter { it.date != date }.sortedByDescending { it.date }.take(limit)

/** 目標に対する達成率(%)。表示が崩れないよう999%で頭打ちにする。目標0のときは0%。 */
fun progressPercent(totalMl: Int, goalMl: Int): Int {
    if (goalMl <= 0) return 0
    return minOf((totalMl.toDouble() / goalMl * 100).roundToInt(), 999)
}
