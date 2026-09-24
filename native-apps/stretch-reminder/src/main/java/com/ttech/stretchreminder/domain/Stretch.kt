package com.ttech.stretchreminder.domain

import kotlinx.serialization.Serializable

/** 順番に提案するストレッチ(Webアプリ版と同じ)。 */
val STRETCHES = listOf(
    "首をゆっくり左右に倒す(各10秒)",
    "肩を大きく前後に回す(10回ずつ)",
    "両腕を上げて体を左右に伸ばす(各10秒)",
    "背もたれに寄りかかり胸を開く(15秒)",
    "座ったまま片足を伸ばし前屈(各15秒)",
    "足首を回す(各10回)",
)

const val DEFAULT_INTERVAL_MIN = 60
val INTERVAL_RANGE_MIN = 10..240

/** 今日の実施回数(日付が変わったら0に戻る)。 */
@Serializable
data class DailyCount(
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val count: Int,
)

/** 前回の実施からの経過分(切り捨て)を引いた、次のストレッチまでの残り分。0未満にはしない。 */
fun remainingMinutes(nowMillis: Long, lastDoneAtMillis: Long, intervalMin: Int): Int {
    val elapsedMin = Math.floorDiv(nowMillis - lastDoneAtMillis, 60_000L).toInt()
    return maxOf(intervalMin - elapsedMin, 0)
}

fun isDue(nowMillis: Long, lastDoneAtMillis: Long, intervalMin: Int): Boolean =
    remainingMinutes(nowMillis, lastDoneAtMillis, intervalMin) <= 0

/** 次に通知する時刻(前回の実施から間隔分後)。 */
fun nextReminderAt(lastDoneAtMillis: Long, intervalMin: Int): Long = lastDoneAtMillis + intervalMin * 60_000L

fun DailyCount?.countOn(today: String): Int = if (this?.date == today) count else 0

/** ストレッチ完了で今日の回数を1増やす(日付が変わっていれば1から)。 */
fun DailyCount?.increment(today: String): DailyCount = DailyCount(today, countOn(today) + 1)

/** 今日の実施回数に応じて、次に提案するストレッチ。 */
fun suggestion(todayCount: Int): String = STRETCHES[todayCount % STRETCHES.size]

/** 入力された間隔(分)。読めなければ60分、10〜240分の範囲に収める。 */
fun parseInterval(text: String): Int = (text.toIntOrNull() ?: DEFAULT_INTERVAL_MIN).coerceIn(INTERVAL_RANGE_MIN)
