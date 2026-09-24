package com.ttech.movielog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovieTest {
    @Test
    fun `入力値から記録を作り前後の空白を除く`() {
        val m = buildMovie("1", " 君の名は。 ", "映画", 5, "2026-09-24", " よかった ")
        assertEquals(Movie("1", "君の名は。", "映画", 5, "2026-09-24", "よかった"), m)
    }

    @Test
    fun `作品名が空なら記録を作らない`() {
        assertNull(buildMovie("1", "  ", "映画", 3, "2026-09-24", ""))
    }

    @Test
    fun `評価は1から5の範囲に収める`() {
        assertEquals(5, buildMovie("1", "a", "ドラマ", 9, "2026-09-24", "")!!.rating)
        assertEquals(1, buildMovie("1", "a", "ドラマ", 0, "2026-09-24", "")!!.rating)
    }

    @Test
    fun `見出しと感想の表示形式`() {
        val m = Movie("1", "半沢直樹", "ドラマ", 4, "2026-09-24", "")
        assertEquals("[ドラマ] 半沢直樹", m.heading())
        assertEquals("", m.memoSuffix())
        assertEquals(" ・ 倍返し", m.copy(memo = "倍返し").memoSuffix())
    }
}
