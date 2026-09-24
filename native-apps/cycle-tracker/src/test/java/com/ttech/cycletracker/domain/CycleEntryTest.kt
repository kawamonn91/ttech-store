package com.ttech.cycletracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CycleEntryTest {
    private fun entry(id: String, date: String) = CycleEntry(id, date)

    @Test
    fun `記録一覧は開始日の新しい順に並ぶ`() {
        val entries = listOf(entry("1", "2024-01-01"), entry("3", "2024-03-01"), entry("2", "2024-02-01"))
        assertEquals(listOf("3", "2", "1"), entries.sortedByStartDateDescending().map { it.id })
    }

    @Test
    fun `記録が2件未満なら設定値の周期をそのまま使う`() {
        assertEquals(28, averageCycleLength(emptyList(), fallback = 28))
        assertEquals(30, averageCycleLength(listOf(entry("1", "2024-01-01")), fallback = 30))
    }

    @Test
    fun `隣接する開始日の差の平均を周期として算出する`() {
        // 新しい順: 3/1, 2/1(29日前), 1/3(29日前) -> 平均29日
        val sorted = listOf(entry("3", "2024-03-01"), entry("2", "2024-02-01"), entry("1", "2024-01-03"))
        assertEquals(29, averageCycleLength(sorted, fallback = 28))
    }

    @Test
    fun `平均は四捨五入される`() {
        // 3/1 と 2/1(29日) 、2/1 と 1/1(31日) -> 平均30日
        val sorted = listOf(entry("3", "2024-03-01"), entry("2", "2024-02-01"), entry("1", "2024-01-01"))
        assertEquals(30, averageCycleLength(sorted, fallback = 28))
    }

    @Test
    fun `次回開始予測は最終開始日に平均周期を足した日`() {
        assertEquals("2024-01-29", predictNextStart("2024-01-01", avgCycleDays = 28))
    }

    @Test
    fun `排卵日周辺は次回予測の18日前から11日前`() {
        val window = fertileWindow("2024-01-01", avgCycleDays = 28)
        assertEquals("2024-01-11", window.start)
        assertEquals("2024-01-18", window.end)
    }
}
