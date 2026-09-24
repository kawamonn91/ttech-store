package com.ttech.propertychecklist.ui

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ttech.propertychecklist.data.PropertyStore
import com.ttech.propertychecklist.domain.Property
import com.ttech.propertychecklist.domain.newProperty
import com.ttech.propertychecklist.domain.progressLabel
import com.ttech.propertychecklist.domain.toggleItem
import com.ttech.propertychecklist.domain.updateNotes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PropertyChecklistScreen() {
    val context = LocalContext.current
    val store = remember { PropertyStore(context) }
    val scope = rememberCoroutineScope()
    val properties by store.properties.collectAsState(initial = emptyList())

    var address by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }

    fun addProperty() {
        if (address.isBlank()) return
        val p = newProperty(address.trim(), LocalDate.now().toString()) { UUID.randomUUID().toString() }
        scope.launch { store.add(p) }
        address = ""
        selectedId = p.id
    }

    fun removeProperty(id: String) {
        scope.launch { store.remove(id) }
        if (selectedId == id) selectedId = null
    }

    val selected = properties.firstOrNull { it.id == selectedId }

    Scaffold(topBar = { TopAppBar(title = { Text("不動産内見チェックリスト") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("物件名・住所を入力") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = ::addProperty, enabled = address.isNotBlank()) { Text("追加") }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "物件一覧",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        if (properties.isEmpty()) {
                            Text(
                                "まだ物件がありません",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        properties.forEach { p ->
                            val isSelected = p.id == selectedId
                            TextButton(onClick = { selectedId = p.id }, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Text(
                                    p.progressLabel(),
                                    fontWeight = if (isSelected) FontWeight.Bold else null,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            if (selected != null) {
                item { SelectedProperty(selected, onRemove = { removeProperty(selected.id) }) { transform -> scope.launch { store.update(transform) } } }
            }
        }
    }
}

@Composable
private fun SelectedProperty(
    property: Property,
    onRemove: () -> Unit,
    onUpdate: (transform: (List<Property>) -> List<Property>) -> Unit,
) {
    // メモは入力のたびに保存し直すが、表示は画面側の状態を正とする(Flow から読み戻すとカーソルが飛ぶため)
    var notes by remember(property.id) { mutableStateOf(property.notes) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(property.address, style = MaterialTheme.typography.titleMedium)
                        Text("内見日 ${property.viewedDate}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onRemove) { Text("削除") }
                }
                property.items.forEach { item ->
                    // Web版と同じく、項目名をタップしても切り替わるように行全体をタップ対象にする。
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = item.checked,
                            role = Role.Checkbox,
                            onValueChange = { onUpdate { it.toggleItem(property.id, item.id) } },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(
                            item.label,
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = notes,
                onValueChange = { v ->
                    notes = v
                    onUpdate { it.updateNotes(property.id, v) }
                },
                label = { Text("メモ") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}
