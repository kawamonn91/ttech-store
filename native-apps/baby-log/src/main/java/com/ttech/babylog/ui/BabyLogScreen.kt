package com.ttech.babylog.ui

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.babylog.data.BabyEventStore
import com.ttech.babylog.domain.BabyEvent
import com.ttech.babylog.domain.EventType
import com.ttech.babylog.domain.formatEventTime
import com.ttech.babylog.domain.lastOf
import com.ttech.babylog.domain.minutesAgo
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BabyLogScreen() {
    val context = LocalContext.current
    val store = remember { BabyEventStore(context) }
    val scope = rememberCoroutineScope()
    val events by store.events.collectAsState(initial = emptyList())

    fun addEvent(type: EventType) {
        val event = BabyEvent(id = UUID.randomUUID().toString(), type = type, at = System.currentTimeMillis())
        scope.launch { store.add(event) }
    }

    val lastFeed = events.lastOf(EventType.FEED)
    val lastDiaper = events.lastOf(EventType.DIAPER)

    Scaffold(topBar = { TopAppBar(title = { Text("授乳・オムツ替え記録") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryCard(
                        label = "前回の授乳",
                        value = lastFeed?.let { "${minutesAgo(it.at, System.currentTimeMillis())}分前" } ?: "記録なし",
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        label = "前回のオムツ替え",
                        value = lastDiaper?.let { "${minutesAgo(it.at, System.currentTimeMillis())}分前" } ?: "記録なし",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { addEvent(EventType.FEED) },
                        modifier = Modifier.weight(1f),
                    ) { Text("授乳を記録") }
                    OutlinedButton(
                        onClick = { addEvent(EventType.DIAPER) },
                        modifier = Modifier.weight(1f),
                    ) { Text("オムツ替えを記録") }
                }
            }

            item {
                Text(
                    "記録一覧(${events.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (events.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(events, key = BabyEvent::id) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${event.type.label} ・ ${formatEventTime(event.at)}", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { scope.launch { store.remove(event.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
