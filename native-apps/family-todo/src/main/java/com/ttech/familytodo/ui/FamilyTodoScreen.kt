package com.ttech.familytodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ttech.familytodo.data.TodoStore
import com.ttech.familytodo.domain.TodoCategory
import com.ttech.familytodo.domain.TodoItem
import com.ttech.familytodo.domain.filterByCategory
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FamilyTodoScreen() {
    val context = LocalContext.current
    val store = remember { TodoStore(context) }
    val scope = rememberCoroutineScope()
    val items by store.items.collectAsState(initial = emptyList())

    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TodoCategory.TODO) }

    fun addItem() {
        if (text.isBlank()) return
        val item = TodoItem(id = UUID.randomUUID().toString(), text = text.trim(), done = false, category = category)
        scope.launch { store.add(item) }
        text = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("家族共有ToDo・買い物リスト") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = { Text("やること・買うものを入力") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = ::addItem) { Text("追加") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TodoCategory.entries.forEach { c ->
                                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                            }
                        }
                    }
                }
            }

            TodoCategory.entries.forEach { cat ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(cat.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val list = items.filterByCategory(cat)
                            if (list.isEmpty()) {
                                Text(
                                    "項目がありません",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            list.forEach { i ->
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = i.done, onCheckedChange = { scope.launch { store.toggle(i.id) } })
                                    Text(
                                        i.text,
                                        modifier = Modifier.weight(1f),
                                        style = if (i.done) {
                                            MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough)
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        },
                                        color = if (i.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    )
                                    TextButton(onClick = { scope.launch { store.remove(i.id) } }) { Text("削除") }
                                }
                            }
                        }
                    }
                }
            }

            if (items.any { it.done }) {
                item {
                    OutlinedButton(onClick = { scope.launch { store.clearDone() } }, modifier = Modifier.fillMaxWidth()) {
                        Text("完了済みを削除")
                    }
                }
            }

            item {
                Text(
                    "※ このリストは今お使いの端末内にのみ保存されます。家族間で共有するには、各自の端末にこのアプリを入れて口頭やメッセージアプリで内容を共有してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
