package com.ttech.tripshiori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ttech.tripshiori.domain.Limits
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.addPacking
import com.ttech.tripshiori.domain.packingProgress
import com.ttech.tripshiori.domain.removePacking
import com.ttech.tripshiori.domain.resetPacking
import com.ttech.tripshiori.domain.togglePacking
import java.util.UUID

@Composable
fun PackingTab(trip: Trip, onUpdate: ((Trip) -> Trip) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var presets by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val (done, total) = trip.packingProgress()

    fun add() {
        if (name.isBlank()) return
        onUpdate { it.addPacking(listOf(name), { UUID.randomUUID().toString() }) }
        name = ""
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("準備できた数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$done / $total", style = MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Limits.MAX_SHORT) },
                    label = { Text("持ち物を追加") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { add() }, enabled = name.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = "追加")
                }
            }
        }
        item {
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { presets = true }) { Text("おすすめから選ぶ") }
                if (done > 0) OutlinedButton(onClick = { confirmReset = true }) { Text("チェックを外す") }
            }
        }
        if (trip.packing.isEmpty()) {
            item { EmptyMessage("持ち物はまだありません", "「おすすめから選ぶ」で、旅行の種類に合わせた持ち物をまとめて追加できます。") }
        }
        items(trip.packing, key = { it.id }) { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = p.checked, onCheckedChange = { onUpdate { t -> t.togglePacking(p.id) } })
                Text(
                    p.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (p.checked) TextDecoration.LineThrough else null,
                    color = if (p.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onUpdate { t -> t.removePacking(p.id) } }) {
                    Icon(Icons.Filled.Close, contentDescription = "削除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (presets) {
        PresetDialog(
            existing = trip.packing.map { it.name.trim().lowercase() }.toSet(),
            onAdd = { names -> onUpdate { it.addPacking(names) { UUID.randomUUID().toString() } }; presets = false },
            onDismiss = { presets = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "チェックを外しますか?",
            message = "すべての持ち物のチェックを外します(持ち物は消えません)。",
            confirmLabel = "外す",
            onConfirm = { onUpdate { it.resetPacking() }; confirmReset = false },
            onDismiss = { confirmReset = false },
        )
    }
}
