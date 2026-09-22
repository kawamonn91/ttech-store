package com.ttech.fastingtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.ttech.fastingtimer.data.FastingStore
import com.ttech.fastingtimer.domain.FastSession
import com.ttech.fastingtimer.domain.formatElapsed
import com.ttech.fastingtimer.domain.hoursElapsed
import com.ttech.fastingtimer.domain.progressPercent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FastingTimerScreen() {
    val context = LocalContext.current
    val store = remember { FastingStore(context) }
    val scope = rememberCoroutineScope()
    val session by store.session.collectAsState(initial = null)
    val history by store.history.collectAsState(initial = emptyList())

    var goalHoursText by remember { mutableStateOf("16") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(session) {
        if (session == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    fun start() {
        val goal = goalHoursText.toIntOrNull()?.coerceIn(1, 48) ?: 16
        scope.launch { store.start(FastSession(startedAt = System.currentTimeMillis(), goalHours = goal)) }
    }

    fun stop() {
        val current = session
        val hours = current?.let { hoursElapsed(System.currentTimeMillis() - it.startedAt) }
        scope.launch { store.stopAndRecord(hours) }
    }

    val elapsedMs = session?.let { now - it.startedAt } ?: 0L
    val progress = session?.let { progressPercent(elapsedMs, it.goalHours) } ?: 0

    Scaffold(topBar = { TopAppBar(title = { Text("断食タイマー") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val current = session
                    if (current != null) {
                        Text(
                            "断食中(目標 ${current.goalHours}時間)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatElapsed(elapsedMs),
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        LinearProgressIndicator(
                            progress = { (progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${progress}%達成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        OutlinedButton(onClick = ::stop, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text("断食を終了する")
                        }
                    } else {
                        Text("目標時間(時間)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = goalHoursText,
                            onValueChange = { goalHoursText = it },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(onClick = ::start, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text("断食を開始する")
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("過去の記録", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            history.forEach { h ->
                                androidx.compose.material3.OutlinedCard {
                                    Text("${h}時間", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
