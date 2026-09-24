package com.ttech.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseTest {
    private fun expense(id: String, date: String, category: Category, amount: Int, memo: String = "") =
        Expense(id, date, category, amount, memo)

    @Test
    fun `年月キーは日付の先頭7文字`() {
        assertEquals("2026-09", monthKeyOf("2026-09-22"))
    }

    @Test
    fun `指定した年月の記録だけに絞り込む`() {
        val expenses = listOf(
            expense("1", "2026-09-01", Category.FOOD, 1000),
            expense("2", "2026-08-31", Category.FOOD, 2000),
            expense("3", "2026-09-30", Category.OTHER, 3000),
        )
        val filtered = expenses.filterByMonth("2026-09")
        assertEquals(setOf("1", "3"), filtered.map { it.id }.toSet())
    }

    @Test
    fun `合計金額はamountの総和`() {
        val expenses = listOf(
            expense("1", "2026-09-01", Category.FOOD, 1000),
            expense("2", "2026-09-02", Category.OTHER, 2500),
        )
        assertEquals(3500, expenses.totalAmount())
    }

    @Test
    fun `記録がなければ合計0`() {
        assertEquals(0, emptyList<Expense>().totalAmount())
    }

    @Test
    fun `カテゴリ別合計はカテゴリの定義順で並ぶ`() {
        val expenses = listOf(
            expense("1", "2026-09-01", Category.OTHER, 500),
            expense("2", "2026-09-02", Category.FOOD, 1000),
            expense("3", "2026-09-03", Category.FOOD, 200),
        )
        val totals = expenses.totalsByCategory()
        assertEquals(listOf(Category.FOOD, Category.OTHER), totals.map { it.category })
        assertEquals(1200, totals.first { it.category == Category.FOOD }.total)
    }

    @Test
    fun `金額0のカテゴリは内訳に含まれない`() {
        val expenses = listOf(expense("1", "2026-09-01", Category.FOOD, 0))
        assertTrue(expenses.totalsByCategory().isEmpty())
    }
}
