package com.ttech.childgrowth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.childgrowth.data.ChildGrowthStore
import com.ttech.childgrowth.domain.GrowthEntry
import com.ttech.childgrowth.domain.chartPoints
import com.ttech.childgrowth.domain.sortedByDateAscending
import com.ttech.childgrowth.domain.sortedByDateDescending
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChildGrowthScreen() {
    val context = LocalContext.current
    val store = remember { ChildGrowthStore(context) }
    val scope = rememberCoroutineScope()
    val childName by store.childName.collectAsState(initial = "")
    val entries by store.entries.collectAsState(initial = emptyList())

    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    fun addEntry() {
        val h = height.toDoubleOrNull()
        val w = weight.toDoubleOrNull()
        if (h == null && w == null) return
        val entry = GrowthEntry(
            id = UUID.randomUUID().toString(),
            date = LocalDate.now().toString(),
            heightCm = h ?: 0.0,
            weightKg = w ?: 0.0,
            recordedAt = System.currentTimeMillis(),
        )
        scope.launch { store.add(entry) }
        height = ""; weight = ""
    }

    val ascending = entries.sortedByDateAscending()

    Scaffold(topBar = { TopAppBar(title = { Text("子供の成長記録") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("お子さまの名前(任意)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = childName,
                            onValueChange = { scope.launch { store.setChildName(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (ascending.size >= 2) {
                item {
                    ChartCard(title = "身長の推移(cm)", values = ascending.map { it.heightCm }, color = Color(0xFF2563EB))
                }
                item {
                    ChartCard(title = "体重の推移(kg)", values = ascending.map { it.weightKg }, color = Color(0xFF16A34A))
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = height,
                                onValueChange = { height = it },
                                label = { Text("身長(cm)") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { weight = it },
                                label = { Text("体重(kg)") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(onClick = ::addEntry, modifier = Modifier.fillMaxWidth()) { Text("記録する") }
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

            val listed = entries.sortedByDateDescending()
            if (listed.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(listed, key = GrowthEntry::id) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${entry.date} ・ ${entry.heightCm}cm ・ ${entry.weightKg}kg",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { scope.launch { store.remove(entry.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, values: List<Double>, color: Color) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            val points = chartPoints(values)
            Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                if (points.isEmpty()) return@Canvas
                val scaleX = size.width / 320f
                val scaleY = size.height / 80f
                val scaled = points.map { Offset(it.x * scaleX, it.y * scaleY) }
                for (i in 0 until scaled.size - 1) {
                    drawLine(color = color, start = scaled[i], end = scaled[i + 1], strokeWidth = 4f)
                }
                scaled.forEach { drawCircle(color = color, radius = 6f, center = it) }
            }
        }
    }
}
