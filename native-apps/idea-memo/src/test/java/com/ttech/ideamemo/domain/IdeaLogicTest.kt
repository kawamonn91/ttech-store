package com.ttech.ideamemo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IdeaLogicTest {
    private fun idea(id: String, title: String = id, category: Category = Category.OTHER, priority: Int = 2, status: Status = Status.IDEA, created: Long = 0, updated: Long = 0, memo: String = "", oneLiner: String = "", reference: String = "") =
        Idea(id = id, title = title, category = category, priority = priority, status = status, createdAtMs = created, updatedAtMs = updated, memo = memo, oneLiner = oneLiner, reference = reference)

    @Test
    fun `絞り込み 状態・カテゴリ・キーワード(タイトル・一言・メモ・参考)`() {
        val list = listOf(
            idea("a", "習慣トラッカー", Category.HEALTH, status = Status.IDEA, memo = "禁煙・筋トレ"),
            idea("b", "レシート家計簿", Category.FINANCE, status = Status.CONSIDERING, oneLiner = "撮るだけ"),
            idea("c", "旅のしおり", Category.HOBBY, status = Status.BUILT, reference = "既存のGoogleカレンダー"),
        )
        assertEquals(listOf("a", "b", "c"), filterIdeas(list, "", null, null).map { it.id })
        assertEquals(listOf("b"), filterIdeas(list, "", Status.CONSIDERING, null).map { it.id })
        assertEquals(listOf("a"), filterIdeas(list, "", null, Category.HEALTH).map { it.id })
        assertEquals(listOf("a"), filterIdeas(list, "禁煙", null, null).map { it.id })
        assertEquals(listOf("b"), filterIdeas(list, "撮るだけ", null, null).map { it.id })
        assertEquals(listOf("c"), filterIdeas(list, "カレンダー", null, null).map { it.id })
        assertEquals(emptyList<String>(), filterIdeas(list, "存在しない", null, null).map { it.id })
    }

    @Test
    fun `検索は英字の大文字小文字を区別しない`() {
        val list = listOf(idea("a", "Habit Tracker"))
        assertEquals(listOf("a"), filterIdeas(list, "HABIT", null, null).map { it.id })
    }

    @Test
    fun `並べ替え`() {
        val list = listOf(
            idea("low", "い", priority = 1, created = 3, updated = 30),
            idea("high-old", "ろ", priority = 3, created = 1, updated = 10),
            idea("high-new", "は", priority = 3, created = 5, updated = 50),
            idea("mid", "に", priority = 2, created = 4, updated = 20),
        )
        assertEquals(listOf("high-new", "high-old", "mid", "low"), sortIdeas(list, SortOrder.PRIORITY).map { it.id })
        assertEquals(listOf("high-new", "mid", "low", "high-old"), sortIdeas(list, SortOrder.NEWEST).map { it.id })
        assertEquals(listOf("high-new", "low", "mid", "high-old"), sortIdeas(list, SortOrder.UPDATED).map { it.id })
        assertEquals(listOf("い", "に", "は", "ろ"), sortIdeas(list, SortOrder.NAME).map { it.title })
    }

    @Test
    fun `統計 状態別・カテゴリ別の件数`() {
        val list = listOf(
            idea("a", status = Status.IDEA, category = Category.HEALTH),
            idea("b", status = Status.IDEA, category = Category.HEALTH),
            idea("c", status = Status.BUILT, category = Category.FINANCE),
        )
        val s = computeStats(list)
        assertEquals(3, s.total)
        assertEquals(mapOf(Status.IDEA to 2, Status.BUILT to 1), s.byStatus)
        assertEquals(mapOf(Category.HEALTH to 2, Category.FINANCE to 1), s.byCategory)
    }

    @Test
    fun `整形 空のタイトル・範囲外の優先度・長すぎる文字を直す`() {
        val messy = Idea(id = " x ", title = "  ", priority = 9, memo = "m".repeat(9000), oneLiner = "o".repeat(500), reference = "r".repeat(500))
        val c = messy.sanitized(nowMs = 5)
        assertEquals("x", c.id)
        assertEquals("(無題)", c.title)
        assertEquals(3, c.priority)
        assertEquals(Limits.MAX_TEXT, c.memo.length)
        assertEquals(Limits.MAX_SHORT, c.oneLiner.length)
        assertEquals(Limits.MAX_SHORT, c.reference.length)
        assertEquals(5, c.updatedAtMs)
        assertEquals(1, messy.copy(priority = 0).sanitized().priority)
    }

    @Test
    fun `星の表示`() {
        assertEquals("", stars(0))
        assertEquals("★", stars(1))
        assertEquals("★★★", stars(3))
        assertEquals("★★★", stars(5)) // 上限で止める
    }
}
