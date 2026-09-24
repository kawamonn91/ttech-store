package com.ttech.cycletracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.cycletracker.data.CycleStore
import com.ttech.cycletracker.domain.CycleEntry
import com.ttech.cycletracker.domain.averageCycleLength
import com.ttech.cycletracker.domain.fertileWindow
import com.ttech.cycletracker.domain.predictNextStart
import com.ttech.cycletracker.domain.sortedByStartDateDescending
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CycleTrackerScreen() {
    val context = LocalContext.current
    val store = remember { CycleStore(context) }
    val scope = rememberCoroutineScope()
    val entries by store.entries.collectAsState(initial = emptyList())
    val cycleLength by store.cycleLengthDays.collectAsState(initial = 28)
    val periodLength by store.periodLengthDays.collectAsState(initial = 5)

    var newDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    fun addEntry() {
        scope.launch { store.add(CycleEntry(id = UUID.randomUUID().toString(), startDate = newDate)) }
    }

    val sorted = entries.sortedByStartDateDescending()
    val lastStart = sorted.firstOrNull()?.startDate
    val avgCycle = averageCycleLength(sorted, cycleLength)
    val nextStart = lastStart?.let { predictNextStart(it, avgCycle) }
    val fertile = lastStart?.let { fertileWindow(it, avgCycle) }

    Scaffold(topBar = { TopAppBar(title = { Text("生理周期予測") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text("生理開始日を記録", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                                    Text(newDate)
                                }
                                Button(onClick = ::addEntry) { Text("記録") }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cycleLength.toString(),
                                onValueChange = { it.toIntOrNull()?.let { v -> scope.launch { store.setCycleLengthDays(v) } } },
                                label = { Text("平均周期(日)") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = periodLength.toString(),
                                onValueChange = { it.toIntOrNull()?.let { v -> scope.launch { store.setPeriodLengthDays(v) } } },
                                label = { Text("生理期間(日)") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (lastStart != null && nextStart != null && fertile != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        ) {
                            Text("次回の生理開始予測", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(nextStart, style = MaterialTheme.typography.headlineSmall)
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                            Text(
                                "排卵日周辺の目安: ${fertile.start} 〜 ${fertile.end}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "実測の平均周期: ${avgCycle}日(${sorted.size}件の記録から算出)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "記録一覧(${entries.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (sorted.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(sorted, key = CycleEntry::id) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(entry.startDate, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { scope.launch { store.remove(entry.id) } }) { Text("削除") }
                    }
                }
            }

            item {
                Text(
                    "※ データは端末内にのみ保存され、外部に送信されません。予測は目安であり医療的な診断ではありません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(newDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                .getOrDefault(System.currentTimeMillis()),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        newDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}
