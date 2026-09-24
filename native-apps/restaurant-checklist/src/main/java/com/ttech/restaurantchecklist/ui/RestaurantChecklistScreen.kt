package com.ttech.restaurantchecklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.restaurantchecklist.data.StockStore
import com.ttech.restaurantchecklist.domain.StockItem
import com.ttech.restaurantchecklist.domain.addItem
import com.ttech.restaurantchecklist.domain.buildStockItem
import com.ttech.restaurantchecklist.domain.formatQty
import com.ttech.restaurantchecklist.domain.needsPrep
import com.ttech.restaurantchecklist.domain.updateQty
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RestaurantChecklistScreen() {
    val context = LocalContext.current
    val store = remember { StockStore(context) }
    val scope = rememberCoroutineScope()
    val items by store.items.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var targetQty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("個") }

    fun addItem() {
        val item = buildStockItem(UUID.randomUUID().toString(), name, targetQty, unit) ?: return
        scope.launch { store.update { it.addItem(item) } }
        name = ""; targetQty = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("仕込み・在庫チェックリスト") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("仕込みが必要な項目", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${items.needsPrep().size}件", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("品目") },
                                singleLine = true,
                                modifier = Modifier.weight(2f),
                            )
                            OutlinedTextField(
                                value = targetQty,
                                onValueChange = { v -> targetQty = v.filter { it.isDigit() || it == '.' }.take(7) },
                                label = { Text("必要量") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("単位(個、kg、Lなど)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = ::addItem,
                            enabled = name.isNotBlank() && targetQty.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("品目を追加") }
                    }
                }
            }

            item {
                Text("在庫チェックリスト", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (items.isEmpty()) {
                item { Text("まだ品目がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(items, key = StockItem::id) { item ->
                StockRow(
                    item = item,
                    onQtyChange = { qty -> scope.launch { store.update { it.updateQty(item.id, qty) } } },
                    onRemove = { scope.launch { store.remove(item.id) } },
                )
            }
        }
    }
}

@Composable
private fun StockRow(item: StockItem, onQtyChange: (Double) -> Unit, onRemove: () -> Unit) {
    // 現在量は入力途中の文字列(「1.」など)を保つため、画面側の状態を正とする
    var qtyText by remember(item.id) { mutableStateOf(formatQty(item.currentQty)) }
    val short = item.needsPrep()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (short) FontWeight.Bold else null,
                    color = if (short) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) { Text("削除") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { v ->
                        qtyText = v.filter { it.isDigit() || it == '.' }.take(7)
                        onQtyChange(qtyText.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("現在量") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(120.dp),
                )
                Text(
                    "/ ${formatQty(item.targetQty)}${item.unit} 必要",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
