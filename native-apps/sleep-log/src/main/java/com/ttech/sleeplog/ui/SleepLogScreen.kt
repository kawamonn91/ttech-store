package com.ttech.sleeplog.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.sleeplog.data.SleepStore
import com.ttech.sleeplog.domain.SleepEntry
import com.ttech.sleeplog.domain.averageHours
import com.ttech.sleeplog.domain.calcDurationHours
import com.ttech.sleeplog.domain.formatHours
import com.ttech.sleeplog.domain.formatTime
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SleepLogScreen() {
    val context = LocalContext.current
    val store = remember { SleepStore(context) }
    val scope = rememberCoroutineScope()
    val entries by store.entries.collectAsState(initial = emptyList())

    var bedTime by remember { mutableStateOf("23:30") }
    var wakeTime by remember { mutableStateOf("07:00") }
    var quality by remember { mutableIntStateOf(3) }
    val duration = calcDurationHours(bedTime, wakeTime)

    fun addEntry() {
        val entry = SleepEntry(
            id = UUID.randomUUID().toString(),
            date = LocalDate.now().toString(),
            bedTime = bedTime,
            wakeTime = wakeTime,
            durationHours = duration,
            quality = quality,
        )
        scope.launch { store.add(entry) }
    }

    val average = averageHours(entries)

    Scaffold(topBar = { TopAppBar(title = { Text("睡眠記録") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimeField("就寝時刻", bedTime, Modifier.weight(1f)) { bedTime = it }
                            TimeField("起床時刻", wakeTime, Modifier.weight(1f)) { wakeTime = it }
                        }
                        Text("睡眠の質", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            (1..5).forEach { q ->
                                SegmentedButton(
                                    selected = quality == q,
                                    onClick = { quality = q },
                                    shape = SegmentedButtonDefaults.itemShape(index = q - 1, count = 5),
                                ) { Text("$q") }
                            }
                        }
                        Text(
                            "睡眠時間: ${formatHours(duration)}時間",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Button(onClick = ::addEntry, modifier = Modifier.fillMaxWidth()) { Text("記録する") }
                    }
                }
            }

            if (average != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("平均睡眠時間", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${formatHours(average)}時間", style = MaterialTheme.typography.headlineSmall)
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

            if (entries.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(entries, key = SleepEntry::id) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${e.date} ・ ${formatHours(e.durationHours)}時間(質${e.quality})", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${e.bedTime} 就寝 → ${e.wakeTime} 起床",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { scope.launch { store.remove(e.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = {
                val (h, m) = value.split(":").map { it.toIntOrNull() ?: 0 }
                TimePickerDialog(context, { _, hour, minute -> onChange(formatTime(hour, minute)) }, h, m, true).show()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text(value, style = MaterialTheme.typography.titleMedium) }
    }
}
