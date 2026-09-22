package com.ttech.fishinglog.ui

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.fishinglog.data.CatchStore
import com.ttech.fishinglog.domain.Catch
import com.ttech.fishinglog.domain.countBySpecies
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FishingLogScreen() {
    val context = LocalContext.current
    val store = remember { CatchStore(context) }
    val scope = rememberCoroutineScope()
    val catches by store.catches.collectAsState(initial = emptyList())

    var location by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }

    fun addCatch() {
        if (species.isBlank()) return
        val item = Catch(
            id = UUID.randomUUID().toString(),
            date = LocalDate.now().toString(),
            location = location.trim(),
            species = species.trim(),
            sizeCm = size.toDoubleOrNull(),
        )
        scope.launch { store.add(item) }
        species = ""; size = ""
    }

    val bySpecies = catches.countBySpecies()

    Scaffold(topBar = { TopAppBar(title = { Text("釣果記録") }) }) { padding ->
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
                        Text("総釣果数", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${catches.size}匹", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("場所") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = species,
                                onValueChange = { species = it },
                                label = { Text("魚種") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = size,
                                onValueChange = { size = it },
                                label = { Text("サイズ(cm、任意)") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(onClick = ::addCatch, enabled = species.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("記録する")
                        }
                    }
                }
            }

            if (bySpecies.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("魚種別内訳", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            bySpecies.forEach { row ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(row.species)
                                    Text("${row.count}匹")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "釣果一覧",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (catches.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(catches, key = Catch::id) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val sizeText = c.sizeCm?.let { " ${it}cm" } ?: ""
                        val locationText = if (c.location.isNotEmpty()) " ・ ${c.location}" else ""
                        Text("${c.date} ・ ${c.species}$sizeText$locationText", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { store.remove(c.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}
