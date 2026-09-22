package com.ttech.expensetracker.domain

import kotlinx.serialization.Serializable

enum class Category(val label: String) {
    FOOD("食費"),
    DAILY("日用品"),
    TRANSPORT("交通費"),
    ENTERTAINMENT("娯楽"),
    HOUSING("住居"),
    OTHER("その他"),
}

@Serializable
data class Expense(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val category: Category,
    val amount: Int,
    val memo: String,
)

/** "2026-09" のような年月キーを取り出す。 */
fun monthKeyOf(dateIso: String): String = dateIso.take(7)

/** 指定した年月の支出だけを残す。 */
fun List<Expense>.filterByMonth(monthKey: String): List<Expense> = filter { monthKeyOf(it.date) == monthKey }

fun List<Expense>.totalAmount(): Int = sumOf { it.amount }

data class CategoryTotal(val category: Category, val total: Int)

/** カテゴリ別の合計。金額が0のカテゴリは含めない(Webアプリ版と同じ)。 */
fun List<Expense>.totalsByCategory(): List<CategoryTotal> {
    val sums = groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    return Category.entries.mapNotNull { c -> sums[c]?.takeIf { it > 0 }?.let { CategoryTotal(c, it) } }
}
