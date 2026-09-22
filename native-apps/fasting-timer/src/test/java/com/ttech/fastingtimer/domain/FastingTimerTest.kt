package com.ttech.fastingtimer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FastingTimerTest {

    @Test
    fun `経過時間はHH_mm_ss形式で表示される`() {
        assertEquals("00:00:00", formatElapsed(0))
        assertEquals("00:00:05", formatElapsed(5_000))
        assertEquals("01:02:03", formatElapsed((1 * 3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun `負の経過時間は0として扱う`() {
        assertEquals("00:00:00", formatElapsed(-5000))
    }

    @Test
    fun `目標に対する達成率をパーセントで返す`() {
        // 目標16時間のうち8時間経過なら50%
        assertEquals(50, progressPercent(8 * 3_600_000L, goalHours = 16))
        assertEquals(100, progressPercent(16 * 3_600_000L, goalHours = 16))
    }

    @Test
    fun `達成率は999パーセントでカンストする`() {
        assertEquals(999, progressPercent(1000 * 3_600_000L, goalHours = 16))
    }

    @Test
    fun `経過時間は小数第1位までの時間に変換される`() {
        assertEquals(1.5, hoursElapsed((1 * 3600 + 30 * 60) * 1000L), 0.0001)
        assertEquals(0.0, hoursElapsed(0), 0.0001)
    }

    @Test
    fun `記録は先頭に追加される`() {
        val history = listOf(8.0, 6.0)
        assertEquals(listOf(10.0, 8.0, 6.0), history.pushHistory(10.0))
    }

    @Test
    fun `記録は最大件数で切り詰められる`() {
        val history = (1..20).map { it.toDouble() }
        val updated = history.pushHistory(0.0, maxSize = 20)
        assertEquals(20, updated.size)
        assertEquals(0.0, updated.first(), 0.0001)
        assertEquals(19.0, updated.last(), 0.0001)
    }
}
