package com.ttech.simpletodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.ttech.simpletodo.data.TodoStore
import com.ttech.simpletodo.domain.TodoItem
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SimpleTodoScreen() {
    val context = LocalContext.current
    val store = remember { TodoStore(context) }
    val scope = rememberCoroutineScope()
    val items by store.items.collectAsState(initial = emptyList())

    var text by remember { mutableStateOf("") }

    fun addItem() {
        if (text.isBlank()) return
        scope.launch { store.add(TodoItem(id = UUID.randomUUID().toString(), text = text.trim(), done = false)) }
        text = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("シンプルToDo") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text("やることを入力") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = ::addItem) { Text("追加") }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        "やることがありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(items, key = { it.id }) { i ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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

            if (items.any { it.done }) {
                item {
                    OutlinedButton(onClick = { scope.launch { store.clearDone() } }, modifier = Modifier.fillMaxWidth()) {
                        Text("完了済みを削除")
                    }
                }
            }
        }
    }
}
