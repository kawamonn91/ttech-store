package com.ttech.stretchreminder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StretchTest {
    private val minute = 60_000L
    private fun state(intervalMin: Int = 60, lastDoneAt: Long = 0, date: String = "2026-09-24", count: Int = 0) =
        StretchState(intervalMin, lastDoneAt, date, count)

    @Test
    fun `残り時間は間隔から経過分を引いた値`() {
        assertEquals(45, remainingMin(state(intervalMin = 60), now = 15 * minute))
    }

    @Test
    fun `経過は切り捨てで数えるので59秒経っても残りは減らない`() {
        assertEquals(60, remainingMin(state(intervalMin = 60), now = 59_000))
        assertEquals(59, remainingMin(state(intervalMin = 60), now = 60_000))
    }

    @Test
    fun `間隔を過ぎたら残り0分で時間になる`() {
        assertEquals(0, remainingMin(state(intervalMin = 30), now = 90 * minute))
        assertTrue(isDue(state(intervalMin = 30), now = 30 * minute))
        assertFalse(isDue(state(intervalMin = 30), now = 29 * minute))
    }

    @Test
    fun `次のリマインド時刻は最後の完了から間隔分後`() {
        assertEquals(40 * minute, nextReminderAt(state(intervalMin = 30, lastDoneAt = 10 * minute)))
    }

    @Test
    fun `完了すると時刻と回数が更新される`() {
        val next = markDone(state(count = 2), now = 5 * minute, today = "2026-09-24")
        assertEquals(5 * minute, next.lastDoneAt)
        assertEquals(3, next.completedCount)
    }

    @Test
    fun `日付が変わったら回数は1から数え直す`() {
        val next = markDone(state(date = "2026-09-23", count = 5), now = 0, today = "2026-09-24")
        assertEquals("2026-09-24", next.completedDate)
        assertEquals(1, next.completedCount)
    }

    @Test
    fun `今日の回数は日付が違えば0`() {
        assertEquals(0, todayCount(state(date = "2026-09-23", count = 4), today = "2026-09-24"))
        assertEquals(4, todayCount(state(date = "2026-09-24", count = 4), today = "2026-09-24"))
    }

    @Test
    fun `提案は実施回数に応じて順番に巡回する`() {
        assertEquals(STRETCHES[0], suggestion(0))
        assertEquals(STRETCHES[1], suggestion(1))
        assertEquals(STRETCHES[0], suggestion(STRETCHES.size))
    }

    @Test
    fun `間隔が空か0なら60分に戻す`() {
        assertEquals(60, normalizeInterval(null))
        assertEquals(60, normalizeInterval(0))
        assertEquals(30, normalizeInterval(30))
    }
}
