package com.ttech.tastingnotes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TastingTest {
    @Test
    fun `入力値から記録を作り前後の空白を除く`() {
        val t = buildTasting("1", " 獺祭 ", "日本酒", 5, "2026-09-24", " フルーティー ")
        assertEquals(Tasting("1", "獺祭", "日本酒", 5, "2026-09-24", "フルーティー"), t)
    }

    @Test
    fun `銘柄名が空なら記録を作らない`() {
        assertNull(buildTasting("1", "", "ワイン", 3, "2026-09-24", "メモ"))
    }

    @Test
    fun `評価は1から5の範囲に収める`() {
        assertEquals(1, buildTasting("1", "a", "その他", -2, "2026-09-24", "")!!.rating)
    }

    @Test
    fun `見出しは種類と銘柄名`() {
        assertEquals("[ワイン] シャブリ", Tasting("1", "シャブリ", "ワイン", 4, "2026-09-24", "").heading())
    }
}
