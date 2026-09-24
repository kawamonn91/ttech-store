package com.ttech.sleeplog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepEntryTest {
    private fun e(hours: Double) = SleepEntry("id$hours", "2026-09-24", "23:00", "07:00", hours, 3)

    @Test
    fun `日をまたぐ睡眠時間を計算する`() {
        assertEquals(7.5, calcDurationHours("23:30", "07:00"), 0.0001)
    }

    @Test
    fun `同じ日の中の睡眠時間を計算する`() {
        assertEquals(1.5, calcDurationHours("13:00", "14:30"), 0.0001)
    }

    @Test
    fun `就寝と起床が同じ時刻なら24時間`() {
        assertEquals(24.0, calcDurationHours("07:00", "07:00"), 0.0001)
    }

    @Test
    fun `睡眠時間は小数第1位に丸める`() {
        // 7時間20分 = 7.333… → 7.3
        assertEquals(7.3, calcDurationHours("23:40", "07:00"), 0.0001)
        // 7時間45分 = 7.75 → 7.8
        assertEquals(7.8, calcDurationHours("23:15", "07:00"), 0.0001)
    }

    @Test
    fun `平均睡眠時間を小数第1位で求める`() {
        assertEquals(7.2, averageHours(listOf(e(7.0), e(7.5), e(7.0)))!!, 0.0001)
    }

    @Test
    fun `記録がなければ平均はnull`() {
        assertNull(averageHours(emptyList()))
    }

    @Test
    fun `時刻と時間の表示形式`() {
        assertEquals("07:05", formatTime(7, 5))
        assertEquals("7", formatHours(7.0))
        assertEquals("7.5", formatHours(7.5))
    }
}
