package com.ttech.weightlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightEntryTest {
    private fun e(id: String, date: String, kg: Double) = WeightEntry(id, date, kg, null)

    @Test
    fun `追加すると日付の新しい順に並ぶ`() {
        val entries = listOf(e("1", "2026-09-20", 70.0), e("2", "2026-09-10", 71.0))
        val next = entries.addEntry(e("3", "2026-09-15", 70.5))
        assertEquals(listOf("1", "3", "2"), next.map { it.id })
    }

    @Test
    fun `同じ日なら後から記録したものが先頭`() {
        val entries = listOf(e("1", "2026-09-20", 70.0))
        val next = entries.addEntry(e("2", "2026-09-20", 69.8))
        assertEquals(listOf("2", "1"), next.map { it.id })
    }

    @Test
    fun `グラフ用の値は古い順`() {
        val entries = listOf(e("3", "2026-09-20", 70.0), e("2", "2026-09-15", 70.5), e("1", "2026-09-10", 71.0))
        assertEquals(listOf(71.0, 70.5, 70.0), entries.weightPointsAscending())
    }

    @Test
    fun `グラフ座標は最大値が上端近く最小値が下端近くになる`() {
        val points = chartPoints(listOf(70.0, 72.0))
        assertEquals(0f, points[0].x, 0.001f)
        assertEquals(95f, points[0].y, 0.001f)
        assertEquals(320f, points[1].x, 0.001f)
        assertEquals(5f, points[1].y, 0.001f)
    }

    @Test
    fun `値が全部同じでもゼロ除算しない`() {
        val points = chartPoints(listOf(70.0, 70.0, 70.0))
        assertTrue(points.all { it.y == 95f })
    }

    @Test
    fun `2点未満ならグラフは描かない`() {
        assertEquals(emptyList<ChartPoint>(), chartPoints(listOf(70.0)))
    }

    @Test
    fun `整数は小数点なしで表示する`() {
        assertEquals("72", formatNumber(72.0))
        assertEquals("72.5", formatNumber(72.5))
    }
}
