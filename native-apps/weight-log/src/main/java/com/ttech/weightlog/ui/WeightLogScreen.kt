package com.ttech.weightlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.weightlog.data.WeightStore
import com.ttech.weightlog.domain.WeightEntry
import com.ttech.weightlog.domain.addEntry
import com.ttech.weightlog.domain.chartPoints
import com.ttech.weightlog.domain.formatNumber
import com.ttech.weightlog.domain.weightPointsAscending
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WeightLogScreen() {
    val context = LocalContext.current
    val store = remember { WeightStore(context) }
    val scope = rememberCoroutineScope()
    val entries by store.entries.collectAsState(initial = emptyList())

    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }

    fun addEntry() {
        val w = weight.toDoubleOrNull() ?: return
        if (w == 0.0) return
        val entry = WeightEntry(
            id = UUID.randomUUID().toString(),
            date = LocalDate.now().toString(),
            weightKg = w,
            bodyFatPercent = bodyFat.toDoubleOrNull(),
        )
        scope.launch { store.update { it.addEntry(entry) } }
        weight = ""; bodyFat = ""
    }

    val latest = entries.firstOrNull()
    val weightPoints = entries.weightPointsAscending()

    Scaffold(topBar = { TopAppBar(title = { Text("体重・体脂肪率グラフ") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (latest != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat("最新の体重", "${formatNumber(latest.weightKg)}kg")
                            Stat("体脂肪率", latest.bodyFatPercent?.let { "${formatNumber(it)}%" } ?: "―")
                        }
                    }
                }
            }

            if (weightPoints.size >= 2) {
                item {
                    val color = MaterialTheme.colorScheme.primary
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("体重の推移", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${formatNumber(weightPoints.max())}kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            val points = chartPoints(weightPoints)
                            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                                val scaleX = size.width / 320f
                                val scaleY = size.height / 100f
                                val scaled = points.map { Offset(it.x * scaleX, it.y * scaleY) }
                                for (i in 0 until scaled.size - 1) {
                                    drawLine(color = color, start = scaled[i], end = scaled[i + 1], strokeWidth = 4f)
                                }
                                scaled.forEach { drawCircle(color = color, radius = 6f, center = it) }
                            }
                            Text(
                                "${formatNumber(weightPoints.min())}kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { v -> weight = v.filter { it.isDigit() || it == '.' }.take(6) },
                                label = { Text("体重(kg)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = bodyFat,
                                onValueChange = { v -> bodyFat = v.filter { it.isDigit() || it == '.' }.take(5) },
                                label = { Text("体脂肪率(%、任意)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(onClick = ::addEntry, enabled = weight.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("記録する")
                        }
                    }
                }
            }

            item {
                Text(
                    "記録一覧(${entries.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entries.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(entries, key = WeightEntry::id) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val fat = e.bodyFatPercent?.let { " ・ ${formatNumber(it)}%" } ?: ""
                        Text("${e.date} ・ ${formatNumber(e.weightKg)}kg$fat", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { store.remove(e.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}
