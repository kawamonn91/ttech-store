package com.ttech.flashcards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ttech.flashcards.data.FlashCardStore
import com.ttech.flashcards.domain.FlashCard
import com.ttech.flashcards.domain.isValidCard
import com.ttech.flashcards.domain.nextIndex
import com.ttech.flashcards.domain.prevIndex
import kotlinx.coroutines.launch
import java.util.UUID

private enum class Mode { EDIT, REVIEW }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen() {
    val context = LocalContext.current
    val store = remember { FlashCardStore(context) }
    val scope = rememberCoroutineScope()
    val cards by store.cards.collectAsState(initial = emptyList())

    var mode by remember { mutableStateOf(Mode.EDIT) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var index by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }

    fun addCard() {
        if (!isValidCard(front, back)) return
        val card = FlashCard(id = UUID.randomUUID().toString(), front = front.trim(), back = back.trim())
        scope.launch { store.add(card) }
        front = ""; back = ""
    }

    fun startReview() {
        index = 0
        flipped = false
        mode = Mode.REVIEW
    }

    Scaffold(topBar = { TopAppBar(title = { Text("フラッシュカード単語帳") }) }) { padding ->
        if (mode == Mode.REVIEW && cards.isNotEmpty()) {
            val card = cards[index % cards.size]
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1} / ${cards.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { mode = Mode.EDIT }) { Text("編集に戻る") }
                }
                Card(
                    Modifier.fillMaxWidth().height(224.dp).clickable { flipped = !flipped },
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            if (flipped) card.back else card.front,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Text(
                    "タップして裏面を表示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { flipped = false; index = prevIndex(index, cards.size) },
                        modifier = Modifier.weight(1f),
                    ) { Text("前へ") }
                    Button(
                        onClick = { flipped = false; index = nextIndex(index, cards.size) },
                        modifier = Modifier.weight(1f),
                    ) { Text("次へ") }
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = front,
                                onValueChange = { front = it },
                                label = { Text("表(問題)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = back,
                                onValueChange = { back = it },
                                label = { Text("裏(答え)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(onClick = ::addCard, enabled = isValidCard(front, back), modifier = Modifier.fillMaxWidth()) {
                                Text("カードを追加")
                            }
                        }
                    }
                }

                if (cards.isNotEmpty()) {
                    item {
                        Button(onClick = ::startReview, modifier = Modifier.fillMaxWidth()) {
                            Text("${cards.size}枚で復習を始める")
                        }
                    }
                }

                item {
                    Text(
                        "カード一覧(${cards.size}枚)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (cards.isEmpty()) {
                    item { Text("まだカードがありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                items(cards, key = FlashCard::id) { c ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${c.front} → ${c.back}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { store.remove(c.id) } }) { Text("削除") }
                        }
                    }
                }
            }
        }
    }
}
