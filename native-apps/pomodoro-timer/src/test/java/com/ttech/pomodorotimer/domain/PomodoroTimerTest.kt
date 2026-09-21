package com.ttech.pomodorotimer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PomodoroTimerTest {
    @Test
    fun `停止中はtickしても変化しない`() {
        val s = PomodoroState.initial().copy(running = false)
        assertEquals(s, PomodoroTimer.tick(s))
    }

    @Test
    fun `動作中は1秒ずつ減る`() {
        val s = PomodoroState.initial(focusMinutes = 1).copy(running = true, secondsLeft = 60)
        val next = PomodoroTimer.tick(s)
        assertEquals(59, next.secondsLeft)
        assertEquals(Phase.FOCUS, next.phase)
    }

    @Test
    fun `集中0秒でbreakへ切り替わり、完了回数が増える`() {
        val s = PomodoroState.initial(focusMinutes = 1, breakMinutes = 5).copy(running = true, secondsLeft = 1, completedFocusSessions = 2)
        val next = PomodoroTimer.tick(s)
        assertEquals(Phase.BREAK, next.phase)
        assertEquals(5 * 60, next.secondsLeft)
        assertEquals(3, next.completedFocusSessions)
    }

    @Test
    fun `break0秒でfocusへ戻り、完了回数は増えない`() {
        val s = PomodoroState.initial(focusMinutes = 25, breakMinutes = 1).copy(phase = Phase.BREAK, running = true, secondsLeft = 1, completedFocusSessions = 1)
        val next = PomodoroTimer.tick(s)
        assertEquals(Phase.FOCUS, next.phase)
        assertEquals(25 * 60, next.secondsLeft)
        assertEquals(1, next.completedFocusSessions)
    }

    @Test
    fun `resetはフォーカスに戻り時間を初期化する`() {
        val s = PomodoroState.initial(focusMinutes = 10).copy(phase = Phase.BREAK, running = true, secondsLeft = 42)
        val r = PomodoroTimer.reset(s)
        assertFalse(r.running)
        assertEquals(Phase.FOCUS, r.phase)
        assertEquals(600, r.secondsLeft)
    }

    @Test
    fun `applyDurationsは実行中でも即座に反映し停止する`() {
        val s = PomodoroState.initial(focusMinutes = 25, breakMinutes = 5).copy(running = true, secondsLeft = 100)
        val r = PomodoroTimer.applyDurations(s, focusMinutes = 15, breakMinutes = 3)
        assertEquals(15, r.focusMinutes)
        assertEquals(3, r.breakMinutes)
        assertFalse(r.running)
        assertEquals(15 * 60, r.secondsLeft)
    }

    @Test
    fun `formatTimeは2桁ゼロ埋め`() {
        assertEquals("00:05", PomodoroTimer.formatTime(5))
        assertEquals("25:00", PomodoroTimer.formatTime(1500))
        assertEquals("09:59", PomodoroTimer.formatTime(599))
    }
}
