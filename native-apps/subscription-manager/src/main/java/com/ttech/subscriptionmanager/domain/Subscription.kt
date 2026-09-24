package com.ttech.subscriptionmanager.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

@Serializable
enum class Cycle(val label: String, val unit: String) { MONTHLY("毎月", "月"), YEARLY("毎年", "年") }

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val amount: Long,
    val cycle: Cycle,
    /** ISO8601 (YYYY-MM-DD) */
    val nextPaymentDate: String,
)

/** 残り日数がこれ以下なら強調表示する(Webアプリ版と同じ3日)。 */
const val DUE_SOON_DAYS = 3

/** 入力値から登録内容を作る。サービス名が空、金額が0・読めない場合は作らない(null)。 */
fun buildSubscription(id: String, name: String, amount: String, cycle: Cycle, nextPaymentDate: String): Subscription? {
    val n = amount.toLongOrNull() ?: return null
    if (name.isBlank() || n == 0L) return null
    return Subscription(id, name.trim(), n, cycle, nextPaymentDate)
}

/** 追加して、次回支払日の近い順に並べ直す。 */
fun List<Subscription>.addSorted(sub: Subscription): List<Subscription> =
    (listOf(sub) + this).sortedBy { it.nextPaymentDate }

/** 月あたりの支払い合計(年払いは12で割る)。円未満は四捨五入。 */
fun List<Subscription>.monthlyTotal(): Long =
    sumOf { if (it.cycle == Cycle.MONTHLY) it.amount.toDouble() else it.amount / 12.0 }.roundToLong()

/** [today] から次回支払日までの日数(過ぎていればマイナス)。 */
fun daysUntil(dateIso: String, today: LocalDate): Long = ChronoUnit.DAYS.between(today, LocalDate.parse(dateIso))

/** 「あと5日」「2日超過」形式。 */
fun remainingLabel(days: Long): String = if (days >= 0) "あと${days}日" else "${-days}日超過"
