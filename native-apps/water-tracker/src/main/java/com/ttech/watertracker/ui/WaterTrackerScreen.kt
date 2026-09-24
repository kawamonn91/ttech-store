package com.ttech.watertracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ttech.watertracker.data.WaterStore
import com.ttech.watertracker.domain.QUICK_AMOUNTS_ML
import com.ttech.watertracker.domain.addAmount
import com.ttech.watertracker.domain.progressPercent
import com.ttech.watertracker.domain.recentExcluding
import com.ttech.watertracker.domain.resetDay
import com.ttech.watertracker.domain.totalOn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WaterTrackerScreen() {
    val context = LocalContext.current
    val store = remember { WaterStore(context) }
    val scope = rememberCoroutineScope()
    val goalMl by store.goalMl.collectAsState(initial = null)
    val logs by store.logs.collectAsState(initial = emptyList())
    var goalText by remember { mutableStateOf("") }
    LaunchedEffect(goalMl) { goalMl?.let { if (goalText.isEmpty()) goalText = it.toString() } }

    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val today = LocalDate.now().toString()
    val todayTotal = logs.totalOn(today)
    val goal = goalMl ?: 0
    val progress = progressPercent(todayTotal, goal)

    Scaffold(topBar = { TopAppBar(title = { Text("水分摂取トラッカー") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今日の摂取量", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${numberFormat.format(todayTotal)}ml", style = MaterialTheme.typography.headlineMedium)
                        LinearProgressIndicator(
                            progress = { minOf(progress, 100) / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        Text(
                            "目標 ${numberFormat.format(goal)}ml 中 $progress%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("記録する", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QUICK_AMOUNTS_ML.forEach { ml ->
                                FilledTonalButton(
                                    onClick = { scope.launch { store.updateLogs { it.addAmount(today, ml) } } },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                ) { Text("+$ml") }
                            }
                        }
                        TextButton(
                            onClick = { scope.launch { store.updateLogs { it.resetDay(today) } } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("今日の記録をリセット") }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { v ->
                            goalText = v.filter { it.isDigit() }.take(6)
                            scope.launch { store.setGoal(goalText.toIntOrNull() ?: 0) }
                        },
                        label = { Text("1日の目標(ml)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }

            val recent = logs.recentExcluding(today)
            if (recent.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("直近の記録", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            recent.forEach { log ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(log.date, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${numberFormat.format(log.totalMl)}ml")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
