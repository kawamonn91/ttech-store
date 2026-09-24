package com.ttech.readinglog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BookTest {
    private fun b(id: String, date: String, pages: Int, author: String = "") = Book(id, "本$id", author, pages, 4, date)

    @Test
    fun `今年読んだ冊数と総ページ数を数える`() {
        val books = listOf(b("1", "2026-09-01", 300), b("2", "2026-01-15", 200), b("3", "2025-12-31", 500))
        assertEquals(YearStats(2, 500), books.statsForYear("2026"))
    }

    @Test
    fun `記録がなければ0冊0ページ`() {
        assertEquals(YearStats(0, 0), emptyList<Book>().statsForYear("2026"))
    }

    @Test
    fun `補足行は著者とページ数があるときだけ出す`() {
        assertEquals("夏目漱石 ・ 320p ・ ", b("1", "2026-09-01", 320, "夏目漱石").detailPrefix())
        assertEquals("320p ・ ", b("1", "2026-09-01", 320).detailPrefix())
        assertEquals("夏目漱石 ・ ", b("1", "2026-09-01", 0, "夏目漱石").detailPrefix())
        assertEquals("", b("1", "2026-09-01", 0).detailPrefix())
    }
}
