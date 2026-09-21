package com.ttech.pomodorotimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ttech.pomodorotimer.data.SessionCountStore
import com.ttech.pomodorotimer.domain.PomodoroState
import com.ttech.pomodorotimer.domain.PomodoroTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PomodoroViewModel(private val store: SessionCountStore) : ViewModel() {
    private val _state = MutableStateFlow(PomodoroState.initial())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val saved = store.completedCount
            saved.collect { count -> _state.value = _state.value.copy(completedFocusSessions = count) }
        }
    }

    fun toggleRunning() {
        _state.value = _state.value.copy(running = !_state.value.running)
        if (_state.value.running) startTicker() else ticker?.cancel()
    }

    fun reset() {
        ticker?.cancel()
        _state.value = PomodoroTimer.reset(_state.value)
    }

    fun applyDurations(focusMinutes: Int, breakMinutes: Int) {
        ticker?.cancel()
        _state.value = PomodoroTimer.applyDurations(_state.value, focusMinutes, breakMinutes)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (_state.value.running) {
                delay(1000)
                val before = _state.value.completedFocusSessions
                _state.value = PomodoroTimer.tick(_state.value)
                if (_state.value.completedFocusSessions != before) store.save(_state.value.completedFocusSessions)
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
    }
}
