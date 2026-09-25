package com.ttech.tripshiori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.tripshiori.domain.normalizeTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** 旅行の期間(開始日〜終了日)を選ぶ。1日だけ選べば日帰りになる */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    start: LocalDate,
    end: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start.toPickerMillis(),
        initialSelectedEndDateMillis = end.toPickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis!!.toPickerDate()
                    val e = state.selectedEndDateMillis?.toPickerDate() ?: s
                    onConfirm(s, e)
                },
            ) { Text("決定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("旅行の期間", Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium) },
            headline = null,
            showModeToggle = false,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** 1日を選ぶ(行った日など) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { onConfirm(state.selectedDateMillis!!.toPickerDate()) },
            ) { Text("決定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    ) { DatePicker(state = state) }
}

/** 時刻を選ぶ。[current] は "HH:mm" か空 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val (h, m) = normalizeTime(current)?.takeIf { it.isNotEmpty() }?.split(":")?.map { it.toInt() } ?: listOf(9, 0)
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("時刻") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { TimePicker(state = state) }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(String.format(java.util.Locale.ROOT, "%02d:%02d", state.hour, state.minute)) }) { Text("決定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
