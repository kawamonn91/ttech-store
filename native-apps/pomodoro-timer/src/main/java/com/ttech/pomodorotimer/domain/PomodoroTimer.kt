package com.ttech.pomodorotimer.domain

enum class Phase { FOCUS, BREAK }

data class PomodoroState(
    val focusMinutes: Int,
    val breakMinutes: Int,
    val phase: Phase,
    val secondsLeft: Int,
    val running: Boolean,
    val completedFocusSessions: Int,
) {
    companion object {
        fun initial(focusMinutes: Int = 25, breakMinutes: Int = 5, completedFocusSessions: Int = 0) = PomodoroState(
            focusMinutes = focusMinutes,
            breakMinutes = breakMinutes,
            phase = Phase.FOCUS,
            secondsLeft = focusMinutes * 60,
            running = false,
            completedFocusSessions = completedFocusSessions,
        )
    }
}

/** タイマーの状態遷移(Web版の setInterval ロジックを、1秒ごとに呼ぶ純粋関数として書き直したもの) */
object PomodoroTimer {
    fun tick(state: PomodoroState): PomodoroState {
        if (!state.running) return state
        if (state.secondsLeft > 1) return state.copy(secondsLeft = state.secondsLeft - 1)

        val next = if (state.phase == Phase.FOCUS) Phase.BREAK else Phase.FOCUS
        val completed = if (state.phase == Phase.FOCUS) state.completedFocusSessions + 1 else state.completedFocusSessions
        val seconds = (if (next == Phase.FOCUS) state.focusMinutes else state.breakMinutes) * 60
        return state.copy(phase = next, secondsLeft = seconds, completedFocusSessions = completed)
    }

    fun reset(state: PomodoroState) = state.copy(running = false, phase = Phase.FOCUS, secondsLeft = state.focusMinutes * 60)

    fun applyDurations(state: PomodoroState, focusMinutes: Int, breakMinutes: Int) = state.copy(
        focusMinutes = focusMinutes,
        breakMinutes = breakMinutes,
        running = false,
        phase = Phase.FOCUS,
        secondsLeft = focusMinutes * 60,
    )

    fun formatTime(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%02d:%02d".format(m, s)
    }
}
