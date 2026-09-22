package com.ttech.dailyfortune.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FortuneTest {

    @Test
    fun `文字列ハッシュはWebアプリ版と同じアルゴリズムで計算される`() {
        // JS版: let h=0; for each char: h = (h*31 + charCode) >>> 0
        // "ab" -> h=97 -> h=97*31+98=3105
        assertEquals(3105L, hashString("ab"))
    }

    @Test
    fun `空文字のハッシュは0`() {
        assertEquals(0L, hashString(""))
    }

    @Test
    fun `同じ日付なら常に同じ運勢になる`() {
        val a = fortuneForDate("2024-01-01")
        val b = fortuneForDate("2024-01-01")
        assertEquals(a, b)
    }

    @Test
    fun `日付が違えば運勢が変わりうる_全種類が出現する`() {
        // 十分な日数を試せば6種類すべてが最低1回は出るはず
        val results = (1..365).map { day ->
            fortuneForDate(LocalDateIsoOf(2024, day))
        }.toSet()
        assertEquals(Fortune.entries.toSet(), results)
    }

    private fun LocalDateIsoOf(year: Int, dayOfYear: Int): String =
        java.time.LocalDate.ofYearDay(year, dayOfYear).toString()
}
