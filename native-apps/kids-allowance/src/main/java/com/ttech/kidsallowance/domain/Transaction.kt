package com.ttech.kidsallowance.domain

import kotlinx.serialization.Serializable
import java.text.NumberFormat

@Serializable
data class Transaction(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val label: String,
    /** 正=もらった、負=使った */
    val amount: Long,
)

enum class TransactionType { IN, OUT }

/**
 * 入力値から記録を作る。金額が0・読めない、または「なにに?」が空なら作らない(null)。
 * つかった場合は金額をマイナスにする(Webアプリ版と同じ)。
 */
fun buildTransaction(id: String, date: String, label: String, amount: String, type: TransactionType): Transaction? {
    val n = amount.toLongOrNull() ?: return null
    if (n == 0L || label.isBlank()) return null
    return Transaction(id, date, label.trim(), if (type == TransactionType.IN) n else -n)
}

fun List<Transaction>.balance(): Long = sumOf { it.amount }

/** 「+500円」「-120円」形式(もらった分には+を付ける)。 */
fun formatSignedYen(amount: Long): String =
    (if (amount >= 0) "+" else "") + NumberFormat.getIntegerInstance(java.util.Locale.JAPAN).format(amount) + "円"
