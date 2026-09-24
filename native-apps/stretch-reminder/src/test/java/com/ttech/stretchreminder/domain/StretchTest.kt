package com.ttech.stretchreminder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StretchTest {
    private val minute = 60_000L
    private val base = 1_800_000_000_000L

    @Test
    fun `残り分は経過分を切り捨てて計算する`() {
        assertEquals(60, remainingMinutes(base, base, 60))
        assertEquals(60, remainingMinutes(base + 59_999, base, 60))
        assertEquals(59, remainingMinutes(base + minute, base, 60))
        assertEquals(1, remainingMinutes(base + 59 * minute + 59_000, base, 60))
    }

    @Test
    fun `間隔を過ぎたら残り0分でストレッチの時間`() {
        assertEquals(0, remainingMinutes(base + 90 * minute, base, 60))
        assertTrue(isDue(base + 60 * minute, base, 60))
        assertFalse(isDue(base + 59 * minute, base, 60))
    }

    @Test
    fun `次の通知は前回の実施から間隔分後`() {
        assertEquals(base + 45 * minute, nextReminderAt(base, 45))
    }

    @Test
    fun `今日の回数は日付が変わると0から数え直す`() {
        val yesterday = DailyCount("2026-09-23", 5)
        assertEquals(0, yesterday.countOn("2026-09-24"))
        assertEquals(DailyCount("2026-09-24", 1), yesterday.increment("2026-09-24"))
        assertEquals(DailyCount("2026-09-24", 3), DailyCount("2026-09-24", 2).increment("2026-09-24"))
        assertEquals(DailyCount("2026-09-24", 1), null.increment("2026-09-24"))
    }

    @Test
    fun `提案するストレッチは回数に応じて順番に巡る`() {
        assertEquals(STRETCHES[0], suggestion(0))
        assertEquals(STRETCHES[1], suggestion(1))
        assertEquals(STRETCHES[0], suggestion(STRETCHES.size))
    }

    @Test
    fun `間隔は10から240分に収め読めなければ60分`() {
        assertEquals(60, parseInterval(""))
        assertEquals(10, parseInterval("3"))
        assertEquals(240, parseInterval("999"))
        assertEquals(45, parseInterval("45"))
    }
}
