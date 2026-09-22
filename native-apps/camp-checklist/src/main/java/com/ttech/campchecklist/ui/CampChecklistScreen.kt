package com.ttech.campchecklist.ui

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
import com.ttech.campchecklist.data.GearStore
import com.ttech.campchecklist.domain.GearItem
import com.ttech.campchecklist.domain.packedProgress
import com.ttech.campchecklist.domain.removeItem
import com.ttech.campchecklist.domain.resetAllPacked
import com.ttech.campchecklist.domain.toggleItem
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CampChecklistScreen() {
    val context = LocalContext.current
    val store = remember { GearStore(context) }
    val scope = rememberCoroutineScope()
    val items by store.items.collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }

    val (packed, total) = packedProgress(items)

    fun update(next: List<GearItem>) {
        scope.launch { store.save(next) }
    }

    fun addItem() {
        if (newName.isBlank()) return
        update(items + GearItem(id = UUID.randomUUID().toString(), name = newName.trim()))
        newName = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("キャンプ持ち物チェック") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("準備状況", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$packed / $total", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("道具を追加") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = ::addItem, enabled = newName.isNotBlank()) { Text("追加") }
                    }
                }
            }

            items(items, key = GearItem::id) { gearItem ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = gearItem.packed, onCheckedChange = { update(toggleItem(items, gearItem.id)) })
                        Text(
                            gearItem.name,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (gearItem.packed) TextDecoration.LineThrough else null,
                            color = if (gearItem.packed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = { update(removeItem(items, gearItem.id)) }) { Text("削除") }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { update(resetAllPacked(items)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) { Text("次回のためにチェックをリセット") }
            }
        }
    }
}
