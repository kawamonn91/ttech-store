package com.ttech.dailyfortune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.ttech.dailyfortune.data.DiaryStore
import com.ttech.dailyfortune.domain.DiaryEntry
import com.ttech.dailyfortune.domain.fortuneForDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DailyFortuneScreen() {
    val context = LocalContext.current
    val store = remember { DiaryStore(context) }
    val scope = rememberCoroutineScope()
    val entries by store.entries.collectAsState(initial = emptyList())

    val today = remember { LocalDate.now().toString() }
    val fortune = remember(today) { fortuneForDate(today) }
    var text by remember { mutableStateOf("") }

    fun addEntry() {
        if (text.isBlank()) return
        // launch のラムダの中で text を直接読むと、コルーチンの実際の開始タイミング次第では
        // 直後の text = "" のクリアより後に評価されてしまい、空文字が保存されることがあるため、
        // 先に値を確定させてから launch に渡す(実機で確認された不具合)。
        val entry = DiaryEntry(id = UUID.randomUUID().toString(), date = today, text = text.trim())
        scope.launch { store.add(entry) }
        text = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("今日の運勢+一言日記") }) }) { padding ->
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
                        Text("${today}の運勢", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fortune.label, style = MaterialTheme.typography.displaySmall)
                        Text(
                            fortune.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("今日の一言日記", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text("今日あったこと、感じたこと") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::addEntry, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("記録する")
                        }
                    }
                }
            }

            item {
                Text(
                    "日記一覧",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entries.isEmpty()) {
                item { Text("まだ日記がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(entries, key = DiaryEntry::id) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${entry.date} ・ ${entry.text}", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { scope.launch { store.remove(entry.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}
