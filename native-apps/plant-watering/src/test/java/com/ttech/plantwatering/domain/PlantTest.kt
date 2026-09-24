package com.ttech.plantwatering.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlantTest {
    private val today = LocalDate.of(2026, 9, 24)
    private fun p(id: String, interval: Int, last: String) = Plant(id, "植物$id", interval, last)

    @Test
    fun `入力値から植物を作り登録日を最終水やり日にする`() {
        assertEquals(Plant("1", "モンステラ", 5, "2026-09-24"), buildPlant("1", " モンステラ ", "5", "2026-09-24"))
    }

    @Test
    fun `間隔が読めない・0以下なら7日で名前が空なら作らない`() {
        assertEquals(7, buildPlant("1", "a", "", "2026-09-24")!!.intervalDays)
        assertEquals(7, buildPlant("1", "a", "0", "2026-09-24")!!.intervalDays)
        assertNull(buildPlant("1", " ", "5", "2026-09-24"))
    }

    @Test
    fun `残り日数と水やり時期の判定`() {
        assertEquals(3L, p("1", 7, "2026-09-20").remainingDays(today))
        assertFalse(p("1", 7, "2026-09-20").isDue(today))
        assertTrue(p("1", 7, "2026-09-17").isDue(today))
        assertEquals(-2L, p("1", 7, "2026-09-15").remainingDays(today))
    }

    @Test
    fun `水やりが近い順に並ぶ`() {
        val plants = listOf(p("a", 7, "2026-09-24"), p("b", 3, "2026-09-20"), p("c", 10, "2026-09-20"))
        assertEquals(listOf("b", "c", "a"), plants.sortedByUrgency(today).map { it.id })
    }

    @Test
    fun `水やり完了で最終水やり日を今日にする`() {
        val plants = listOf(p("a", 7, "2026-09-10"), p("b", 7, "2026-09-10"))
        val next = plants.waterNow("a", "2026-09-24")
        assertEquals("2026-09-24", next[0].lastWateredDate)
        assertEquals("2026-09-10", next[1].lastWateredDate)
    }

    @Test
    fun `状態の表示`() {
        assertEquals("あと3日", p("1", 7, "2026-09-20").statusLabel(today))
        assertEquals("水やりの時期です(0日超過)", p("1", 7, "2026-09-17").statusLabel(today))
        assertEquals("水やりの時期です(2日超過)", p("1", 7, "2026-09-15").statusLabel(today))
    }
}
