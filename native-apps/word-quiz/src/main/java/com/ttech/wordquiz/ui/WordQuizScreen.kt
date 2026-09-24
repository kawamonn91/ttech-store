package com.ttech.wordquiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.wordquiz.data.ScoreStore
import com.ttech.wordquiz.domain.ChoiceState
import com.ttech.wordquiz.domain.Deck
import com.ttech.wordquiz.domain.Score
import com.ttech.wordquiz.domain.choiceState
import com.ttech.wordquiz.domain.questionAt
import com.ttech.wordquiz.domain.shuffledChoices
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WordQuizScreen() {
    val context = LocalContext.current
    val store = remember { ScoreStore(context) }
    val scope = rememberCoroutineScope()
    val score by store.score.collectAsState(initial = Score())

    var deck by remember { mutableStateOf(Deck.KANJI) }
    var index by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }

    val current = deck.questionAt(index)

    fun selectChoice(choice: String) {
        if (selected != null) return
        selected = choice
        if (choice == current.answer) {
            val d = deck
            scope.launch { store.update { it.increment(d) } }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("頻出語クイズ") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Deck.entries.forEachIndexed { i, d ->
                        SegmentedButton(
                            selected = deck == d,
                            onClick = { deck = d; index = 0; selected = null },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = Deck.entries.size),
                        ) { Text(d.label) }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("正解数", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${score.of(deck)}問", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(current.question, style = MaterialTheme.typography.titleMedium)
                        current.shuffledChoices(index).forEach { c ->
                            when (choiceState(c, current.answer, selected)) {
                                ChoiceState.CORRECT -> Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("$c ✓") }
                                ChoiceState.WRONG_SELECTED -> OutlinedButton(
                                    onClick = {},
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("$c ✗") }
                                ChoiceState.NEUTRAL -> OutlinedButton(onClick = { selectChoice(c) }, modifier = Modifier.fillMaxWidth()) { Text(c) }
                            }
                        }
                    }
                }
            }

            if (selected != null) {
                item {
                    Button(onClick = { selected = null; index++ }, modifier = Modifier.fillMaxWidth()) { Text("次の問題") }
                }
            }
        }
    }
}
