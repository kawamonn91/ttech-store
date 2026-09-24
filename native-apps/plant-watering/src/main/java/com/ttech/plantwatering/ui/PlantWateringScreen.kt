package com.ttech.plantwatering.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import com.ttech.plantwatering.data.PlantStore
import com.ttech.plantwatering.domain.Plant
import com.ttech.plantwatering.domain.buildPlant
import com.ttech.plantwatering.domain.isDue
import com.ttech.plantwatering.domain.sortedByUrgency
import com.ttech.plantwatering.domain.statusLabel
import com.ttech.plantwatering.domain.waterNow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlantWateringScreen() {
    val context = LocalContext.current
    val store = remember { PlantStore(context) }
    val scope = rememberCoroutineScope()
    val plants by store.plants.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var intervalDays by remember { mutableStateOf("7") }

    val today = LocalDate.now()

    fun addPlant() {
        val plant = buildPlant(UUID.randomUUID().toString(), name, intervalDays, today.toString()) ?: return
        scope.launch { store.add(plant) }
        name = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("観葉植物の水やり") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("植物の名前") },
                            placeholder = { Text("例: モンステラ") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = intervalDays,
                            onValueChange = { v -> intervalDays = v.filter { it.isDigit() }.take(3) },
                            label = { Text("水やりの間隔(日)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::addPlant, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("登録する")
                        }
                    }
                }
            }

            if (plants.isEmpty()) {
                item {
                    Text(
                        "まだ植物が登録されていません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }

            items(plants.sortedByUrgency(today), key = Plant::id) { p ->
                val due = p.isDue(today)
                OutlinedCard(
                    Modifier.fillMaxWidth(),
                    border = BorderStroke(if (due) 2.dp else 1.dp, if (due) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${p.statusLabel(today)} ・ 最終: ${p.lastWateredDate}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (due) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (due) FontWeight.Medium else null,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            FilledTonalButton(onClick = { scope.launch { store.update { it.waterNow(p.id, today.toString()) } } }) {
                                Text("水やり完了")
                            }
                            TextButton(onClick = { scope.launch { store.remove(p.id) } }) { Text("削除") }
                        }
                    }
                }
            }
        }
    }
}
