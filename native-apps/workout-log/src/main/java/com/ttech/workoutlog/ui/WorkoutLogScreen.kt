package com.ttech.workoutlog.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.workoutlog.data.WorkoutStore
import com.ttech.workoutlog.domain.WorkoutEntry
import com.ttech.workoutlog.domain.buildEntry
import com.ttech.workoutlog.domain.exerciseNames
import com.ttech.workoutlog.domain.summary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen() {
    val context = LocalContext.current
    val store = remember { WorkoutStore(context) }
    val scope = rememberCoroutineScope()
    val entries by store.entries.collectAsState(initial = emptyList())

    var exercise by remember { mutableStateOf("ベンチプレス") }
    var weightKg by remember { mutableStateOf("40") }
    var reps by remember { mutableStateOf("10") }
    var sets by remember { mutableStateOf("3") }

    fun addEntry() {
        val entry = buildEntry(UUID.randomUUID().toString(), LocalDate.now().toString(), exercise, weightKg, reps, sets)
        scope.launch { store.add(entry) }
    }

    val suggestions = entries.exerciseNames().filter { it != exercise }

    Scaffold(topBar = { TopAppBar(title = { Text("筋トレ記録") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exercise,
                            onValueChange = { exercise = it },
                            label = { Text("種目") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (suggestions.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggestions.forEach { name ->
                                    SuggestionChip(onClick = { exercise = name }, label = { Text(name) })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("重量(kg)", weightKg, decimal = true, modifier = Modifier.weight(1f)) { weightKg = it }
                            NumberField("回数", reps, modifier = Modifier.weight(1f)) { reps = it }
                            NumberField("セット数", sets, modifier = Modifier.weight(1f)) { sets = it }
                        }
                        Button(onClick = ::addEntry, modifier = Modifier.fillMaxWidth()) { Text("記録する") }
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

            items(entries, key = WorkoutEntry::id) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.exercise, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${e.date} ・ ${e.summary()}",
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
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
