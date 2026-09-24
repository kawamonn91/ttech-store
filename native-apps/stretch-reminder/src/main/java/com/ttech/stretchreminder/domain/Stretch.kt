package com.ttech.stretchreminder.domain

val STRETCHES = listOf(
    "首をゆっくり左右に倒す(各10秒)",
    "肩を大きく前後に回す(10回ずつ)",
    "両腕を上げて体を左右に伸ばす(各10秒)",
    "背もたれに寄りかかり胸を開く(15秒)",
    "座ったまま片足を伸ばし前屈(各15秒)",
    "足首を回す(各10回)",
)

const val DEFAULT_INTERVAL_MIN = 60
private const val MILLIS_PER_MINUTE = 60_000L

data class StretchState(
    val intervalMin: Int,
    /** 最後にストレッチを終えた時刻(epoch ms)。 */
    val lastDoneAt: Long,
    /** 「今日の実施回数」の対象日(ISO8601)。 */
    val completedDate: String,
    val completedCount: Int,
)

/** 入力が空・0・数字でない場合は既定値に戻す(Web版の `Number(value) || 60` と同じ)。 */
fun normalizeInterval(input: Int?): Int = if (input == null || input == 0) DEFAULT_INTERVAL_MIN else input

fun remainingMin(state: StretchState, now: Long): Int {
    val elapsedMin = Math.floorDiv(now - state.lastDoneAt, MILLIS_PER_MINUTE).toInt()
    return maxOf(state.intervalMin - elapsedMin, 0)
}

fun isDue(state: StretchState, now: Long): Boolean = remainingMin(state, now) <= 0

/** 次のリマインドを出す時刻(epoch ms)。 */
fun nextReminderAt(state: StretchState): Long = state.lastDoneAt + state.intervalMin * MILLIS_PER_MINUTE

/** 日付が変わっていれば0回として扱う。 */
fun todayCount(state: StretchState, today: String): Int =
    if (state.completedDate == today) state.completedCount else 0

/** 今日の実施回数に応じて、順番にストレッチを提案する。 */
fun suggestion(todayCount: Int): String = STRETCHES[todayCount % STRETCHES.size]

fun markDone(state: StretchState, now: Long, today: String): StretchState =
    state.copy(
        lastDoneAt = now,
        completedDate = today,
        completedCount = todayCount(state, today) + 1,
    )
