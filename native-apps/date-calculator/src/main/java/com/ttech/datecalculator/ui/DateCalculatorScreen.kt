package com.ttech.datecalculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.datecalculator.domain.DateCalculator
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Mode(val label: String) {
    OFFSET("○日後/前"),
    AGE("年齢計算"),
    BETWEEN("経過日数"),
}

private val displayFormat = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN)

private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.withWeekday(): String {
    val weekday = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN)
    return "${format(displayFormat)}($weekday)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateCalculatorScreen() {
    var mode by remember { mutableStateOf(Mode.OFFSET) }

    Scaffold(topBar = { TopAppBar(title = { Text("日付計算") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Mode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Mode.entries.size),
                        ) { Text(m.label) }
                    }
                }
            }

            item {
                when (mode) {
                    Mode.OFFSET -> OffsetCalculator()
                    Mode.AGE -> AgeCalculator()
                    Mode.BETWEEN -> BetweenCalculator()
                }
            }
        }
    }
}

@Composable
private fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(date.withWeekday(), modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it.toLocalDate()) }
                    showPicker = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ResultCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun OffsetCalculator() {
    var base by remember { mutableStateOf(LocalDate.now()) }
    var daysText by remember { mutableStateOf("30") }
    var direction by remember { mutableStateOf(1) }

    val days = daysText.toIntOrNull() ?: 0
    val result = DateCalculator.offset(base, days, direction)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField("基準日", base) { base = it }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { v -> daysText = v.filter { it.isDigit() } },
                    label = { Text("日数") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(selected = direction == 1, onClick = { direction = 1 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("後") }
                    SegmentedButton(selected = direction == -1, onClick = { direction = -1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("前") }
                }
            }
        }
    }
    Spacer(Modifier.size(12.dp))
    ResultCard("結果", result.withWeekday())
}

@Composable
private fun AgeCalculator() {
    var birth by remember { mutableStateOf(LocalDate.of(2000, 1, 1)) }
    var asOf by remember { mutableStateOf(LocalDate.now()) }
    val age = DateCalculator.age(birth, asOf)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField("生年月日", birth) { birth = it }
            DateField("基準日", asOf) { asOf = it }
        }
    }
    Spacer(Modifier.size(12.dp))
    ResultCard("年齢", "${age}歳")
}

@Composable
private fun BetweenCalculator() {
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    val days = DateCalculator.daysBetween(start, end)
    val format = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField("開始日", start) { start = it }
            DateField("終了日", end) { end = it }
        }
    }
    Spacer(Modifier.size(12.dp))
    ResultCard("経過日数", "${format.format(days)}日")
}
