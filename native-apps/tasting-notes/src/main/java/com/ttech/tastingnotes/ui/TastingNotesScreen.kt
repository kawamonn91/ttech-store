package com.ttech.tastingnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ttech.tastingnotes.data.TastingStore
import com.ttech.tastingnotes.domain.TASTING_KINDS
import com.ttech.tastingnotes.domain.Tasting
import com.ttech.tastingnotes.domain.buildTasting
import com.ttech.tastingnotes.domain.heading
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private val StarColor = Color(0xFFFBBF24)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TastingNotesScreen() {
    val context = LocalContext.current
    val store = remember { TastingStore(context) }
    val scope = rememberCoroutineScope()
    val items by store.items.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TASTING_KINDS.first()) }
    var rating by remember { mutableIntStateOf(4) }
    var notes by remember { mutableStateOf("") }

    fun addItem() {
        val item = buildTasting(UUID.randomUUID().toString(), name, kind, rating, LocalDate.now().toString(), notes) ?: return
        scope.launch { store.add(item) }
        name = ""; notes = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("テイスティングメモ") }) }) { padding ->
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
                            label = { Text("銘柄名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            TASTING_KINDS.forEachIndexed { index, k ->
                                SegmentedButton(
                                    selected = kind == k,
                                    onClick = { kind = k },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TASTING_KINDS.size),
                                ) { Text(k) }
                            }
                        }
                        Text("評価", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            (1..5).forEach { r ->
                                SegmentedButton(
                                    selected = rating == r,
                                    onClick = { rating = r },
                                    shape = SegmentedButtonDefaults.itemShape(index = r - 1, count = 5),
                                ) { Text("$r") }
                            }
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("テイスティングメモ") },
                            placeholder = { Text("香り、味わい、余韻など") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::addItem, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("記録する")
                        }
                    }
                }
            }

            item {
                Text(
                    "記録一覧(${items.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (items.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(items, key = Tasting::id) { t ->
                val emptyStar = MaterialTheme.colorScheme.outlineVariant
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.heading(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { store.remove(t.id) } }) { Text("削除") }
                        }
                        Text(
                            buildAnnotatedString {
                                append("${t.date} ・ ")
                                withStyle(SpanStyle(color = StarColor)) { append("★".repeat(t.rating)) }
                                withStyle(SpanStyle(color = emptyStar)) { append("★".repeat(5 - t.rating)) }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (t.notes.isNotEmpty()) {
                            Text(t.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
