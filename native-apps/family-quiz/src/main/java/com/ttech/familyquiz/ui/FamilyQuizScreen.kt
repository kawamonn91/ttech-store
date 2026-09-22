package com.ttech.familyquiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.familyquiz.data.QuestionStore
import com.ttech.familyquiz.domain.Question
import com.ttech.familyquiz.domain.isCorrect
import com.ttech.familyquiz.domain.isValidQuestion
import kotlinx.coroutines.launch
import java.util.UUID

private enum class Mode { EDIT, PLAY }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FamilyQuizScreen() {
    val context = LocalContext.current
    val store = remember { QuestionStore(context) }
    val scope = rememberCoroutineScope()
    val questions by store.questions.collectAsState(initial = emptyList())

    var mode by remember { mutableStateOf(Mode.EDIT) }
    var questionText by remember { mutableStateOf("") }
    var choiceTexts by remember { mutableStateOf(listOf("", "", "", "")) }
    var correctIndex by remember { mutableStateOf(0) }

    var playIndex by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var answered by remember { mutableStateOf<Int?>(null) }

    fun addQuestion() {
        if (!isValidQuestion(questionText, choiceTexts)) return
        val q = Question(
            id = UUID.randomUUID().toString(),
            question = questionText.trim(),
            choices = choiceTexts.map { it.trim() },
            correctIndex = correctIndex,
        )
        scope.launch { store.add(q) }
        questionText = ""
        choiceTexts = listOf("", "", "", "")
        correctIndex = 0
    }

    fun startPlay() {
        playIndex = 0
        score = 0
        answered = null
        mode = Mode.PLAY
    }

    fun selectAnswer(i: Int) {
        if (answered != null) return
        answered = i
        if (questions[playIndex].isCorrect(i)) score += 1
    }

    fun nextQuestion() {
        answered = null
        playIndex += 1
    }

    Scaffold(topBar = { TopAppBar(title = { Text("内輪ネタクイズ作成") }) }) { padding ->
        if (mode == Mode.PLAY && questions.isNotEmpty()) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (playIndex >= questions.size) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("結果", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$score / ${questions.size}問正解", style = MaterialTheme.typography.headlineSmall)
                            Button(onClick = { mode = Mode.EDIT }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Text("編集に戻る")
                            }
                        }
                    }
                } else {
                    val q = questions[playIndex]
                    Text("第${playIndex + 1}問 / 全${questions.size}問", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(q.question, style = MaterialTheme.typography.bodyLarge)
                            q.choices.forEachIndexed { i, choice ->
                                val isCorrectChoice = i == q.correctIndex
                                val isSelected = i == answered
                                val showResult = answered != null
                                val colors = when {
                                    showResult && isCorrectChoice -> CardDefaults.outlinedCardColors(containerColor = Color(0xFFDCFCE7))
                                    showResult && isSelected -> CardDefaults.outlinedCardColors(containerColor = Color(0xFFFEE2E2))
                                    else -> CardDefaults.outlinedCardColors()
                                }
                                androidx.compose.material3.OutlinedCard(
                                    onClick = { selectAnswer(i) },
                                    colors = colors,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(choice, Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                    if (answered != null) {
                        Button(onClick = ::nextQuestion, modifier = Modifier.fillMaxWidth()) { Text("次へ") }
                    }
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
                                value = questionText,
                                onValueChange = { questionText = it },
                                label = { Text("問題文") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            choiceTexts.forEachIndexed { i, c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = correctIndex == i, onClick = { correctIndex = i })
                                    OutlinedTextField(
                                        value = c,
                                        onValueChange = { v -> choiceTexts = choiceTexts.toMutableList().also { it[i] = v } },
                                        placeholder = { Text("選択肢${i + 1}(正解にはラジオボタンを選択)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            Button(onClick = ::addQuestion, modifier = Modifier.fillMaxWidth()) { Text("問題を追加") }
                        }
                    }
                }

                if (questions.isNotEmpty()) {
                    item {
                        Button(onClick = ::startPlay, modifier = Modifier.fillMaxWidth()) {
                            Text("${questions.size}問でクイズを始める")
                        }
                    }
                }

                item {
                    Text(
                        "問題一覧(${questions.size}問)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (questions.isEmpty()) {
                    item { Text("まだ問題がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                items(questions, key = Question::id) { q ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(q.question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { store.remove(q.id) } }) { Text("削除") }
                        }
                    }
                }
            }
        }
    }
}
