package com.ttech.warikan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.ttech.warikan.data.ParticipantStore
import com.ttech.warikan.domain.Participant
import com.ttech.warikan.domain.RoundingMode
import com.ttech.warikan.domain.Warikan
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarikanScreen() {
    val context = LocalContext.current
    val store = remember { ParticipantStore(context) }
    val scope = rememberCoroutineScope()
    val participants by store.participants.collectAsState(initial = ParticipantStore.defaultParticipants())

    var totalText by remember { mutableStateOf("") }
    var rounding by remember { mutableStateOf(RoundingMode.UP) }

    val totalYen = totalText.toIntOrNull() ?: 0
    val result = Warikan.calculate(totalYen, participants.size, rounding)
    val yenFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    fun update(newList: List<Participant>) = scope.launch { store.save(newList) }

    Scaffold(topBar = { TopAppBar(title = { androidx.compose.material3.Text("割り勘計算") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("合計金額(円)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = totalText,
                            onValueChange = { v -> totalText = v.filter { it.isDigit() } },
                            placeholder = { Text("例: 10000") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("参加者(${participants.size}人)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = { update(participants + Participant(System.currentTimeMillis().toString(), "参加者${participants.size + 1}")) }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("追加", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        participants.forEachIndexed { index, p ->
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = p.name,
                                    onValueChange = { name -> update(participants.toMutableList().also { it[index] = p.copy(name = name) }) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { update(participants.filterNot { it.id == p.id }) }, enabled = participants.size > 1) {
                                    Icon(Icons.Filled.Delete, contentDescription = "${p.name}を削除")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("端数の処理", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RoundingMode.entries.forEach { mode ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = rounding == mode, onClick = { rounding = mode })
                                Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("1人あたり", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${yenFormat.format(result.perPerson)}円",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (result.remainder != 0) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            val sign = if (result.remainder > 0) "+" else ""
                            val note = if (result.remainder < 0) "幹事の取り分が増えます" else "幹事の負担になります"
                            Text(
                                "端数 $sign${yenFormat.format(result.remainder)}円($note)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
