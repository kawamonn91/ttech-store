package com.ttech.examcountdown.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExamCountdownTest {

    @Test
    fun `試験日が未来なら残り日数を返す`() {
        assertEquals(10L, daysUntil(examDateIso = "2024-01-11", todayIso = "2024-01-01"))
    }

    @Test
    fun `試験日が今日なら0`() {
        assertEquals(0L, daysUntil(examDateIso = "2024-01-01", todayIso = "2024-01-01"))
    }

    @Test
    fun `試験日を過ぎていれば負の値になる_経過日数として使う`() {
        val remaining = daysUntil(examDateIso = "2024-01-01", todayIso = "2024-01-05")
        assertEquals(-4L, remaining)
    }

    @Test
    fun `累計学習時間は全セッションの分数の合計`() {
        val sessions = listOf(
            StudySession("1", "2024-01-01", 30),
            StudySession("2", "2024-01-02", 45),
        )
        assertEquals(75, totalMinutes(sessions))
    }

    @Test
    fun `記録がなければ累計0`() {
        assertEquals(0, totalMinutes(emptyList()))
    }

    @Test
    fun `時間と分の表示形式になる`() {
        assertEquals("1時間15分", formatHoursAndMinutes(75))
        assertEquals("0時間5分", formatHoursAndMinutes(5))
        assertEquals("2時間0分", formatHoursAndMinutes(120))
    }

    @Test
    fun `タイマー表示はmm_ss形式で0埋めされる`() {
        assertEquals("00:00", formatElapsed(0))
        assertEquals("00:05", formatElapsed(5))
        assertEquals("01:05", formatElapsed(65))
        assertEquals("10:00", formatElapsed(600))
    }
}
