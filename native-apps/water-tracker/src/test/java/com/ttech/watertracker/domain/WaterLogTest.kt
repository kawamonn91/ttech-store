package com.ttech.watertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WaterLogTest {
    @Test
    fun `その日の記録がなければ先頭に作る`() {
        val logs = listOf(DailyLog("2026-09-23", 1500))
        val next = logs.addAmount("2026-09-24", 200)
        assertEquals(listOf(DailyLog("2026-09-24", 200), DailyLog("2026-09-23", 1500)), next)
    }

    @Test
    fun `その日の記録があれば合計に足す`() {
        val logs = listOf(DailyLog("2026-09-24", 200))
        assertEquals(550, logs.addAmount("2026-09-24", 350).totalOn("2026-09-24"))
    }

    @Test
    fun `合計はマイナスにならない`() {
        val logs = listOf(DailyLog("2026-09-24", 100))
        assertEquals(0, logs.addAmount("2026-09-24", -500).totalOn("2026-09-24"))
        assertEquals(0, emptyList<DailyLog>().addAmount("2026-09-24", -100).totalOn("2026-09-24"))
    }

    @Test
    fun `記録のない日の合計は0`() {
        assertEquals(0, emptyList<DailyLog>().totalOn("2026-09-24"))
    }

    @Test
    fun `リセットはその日の記録だけ消す`() {
        val logs = listOf(DailyLog("2026-09-24", 200), DailyLog("2026-09-23", 1500))
        assertEquals(listOf(DailyLog("2026-09-23", 1500)), logs.resetDay("2026-09-24"))
    }

    @Test
    fun `直近の記録は今日を除き新しい順に7件まで`() {
        val logs = (1..10).map { DailyLog("2026-09-%02d".format(it), it * 100) }
        val recent = logs.recentExcluding("2026-09-10")
        assertEquals(7, recent.size)
        assertEquals("2026-09-09", recent.first().date)
        assertEquals("2026-09-03", recent.last().date)
    }

    @Test
    fun `達成率は四捨五入し999パーセントで頭打ち`() {
        assertEquals(50, progressPercent(1000, 2000))
        assertEquals(33, progressPercent(666, 2000))
        assertEquals(999, progressPercent(100_000, 2000))
    }

    @Test
    fun `目標が0なら達成率は0`() {
        assertEquals(0, progressPercent(500, 0))
    }
}
