package com.ttech.goshuincho.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.goshuincho.data.GoshuinStore
import com.ttech.goshuincho.domain.GoshuinEntry
import com.ttech.goshuincho.domain.countByPrefecture
import com.ttech.goshuincho.domain.isValidEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoshuinchoScreen() {
    val context = LocalContext.current
    val store = remember { GoshuinStore(context) }
    val scope = rememberCoroutineScope()
    val entries by store.entries.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var prefecture by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var memo by remember { mutableStateOf("") }

    fun addEntry() {
        if (!isValidEntry(name)) return
        val entry = GoshuinEntry(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            prefecture = prefecture.trim(),
            date = date,
            memo = memo.trim(),
        )
        scope.launch { store.add(entry) }
        name = ""; memo = ""
    }

    val byPrefecture = entries.countByPrefecture()

    Scaffold(topBar = { TopAppBar(title = { Text("御朱印帳デジタル記録") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("いただいた御朱印", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${entries.size}体", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("神社・寺院名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = prefecture,
                                onValueChange = { prefecture = it },
                                label = { Text("都道府県") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                                Text(date)
                            }
                        }
                        OutlinedTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            label = { Text("メモ(任意)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::addEntry, enabled = isValidEntry(name), modifier = Modifier.fillMaxWidth()) {
                            Text("記録する")
                        }
                    }
                }
            }

            if (byPrefecture.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("都道府県別内訳", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            byPrefecture.forEach { row ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(row.prefecture)
                                    Text("${row.count}体")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "記録一覧",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entries.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(entries, key = GoshuinEntry::id) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.bodyMedium)
                            val prefectureText = if (e.prefecture.isNotEmpty()) " ・ ${e.prefecture}" else ""
                            val memoText = if (e.memo.isNotEmpty()) " ・ ${e.memo}" else ""
                            Text(
                                "${e.date}$prefectureText$memoText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { scope.launch { store.remove(e.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrDefault(System.currentTimeMillis()),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}
