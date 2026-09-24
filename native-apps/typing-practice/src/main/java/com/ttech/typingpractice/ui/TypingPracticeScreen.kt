package com.ttech.typingpractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ttech.typingpractice.domain.CharState
import com.ttech.typingpractice.domain.TypingResult
import com.ttech.typingpractice.domain.addResult
import com.ttech.typingpractice.domain.averageWpm
import com.ttech.typingpractice.domain.calcResult
import com.ttech.typingpractice.domain.charStates
import com.ttech.typingpractice.domain.pickSentence

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TypingPracticeScreen() {
    var target by remember { mutableStateOf(pickSentence()) }
    var input by remember { mutableStateOf("") }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var results by remember { mutableStateOf(emptyList<TypingResult>()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(target) { focusRequester.requestFocus() }

    fun handleChange(value: String) {
        val now = System.currentTimeMillis()
        val start = startedAt ?: if (value.isNotEmpty()) now.also { startedAt = it } else null
        input = value
        if (value == target) {
            results = results.addResult(calcResult(target, value, start?.let { now - it } ?: 0))
            target = pickSentence(target)
            input = ""
            startedAt = null
        }
    }

    val average = results.averageWpm()
    val correctColor = MaterialTheme.colorScheme.primary
    val wrongColor = MaterialTheme.colorScheme.error
    val pendingColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(topBar = { TopAppBar(title = { Text("タイピング練習") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "この文をローマ字またはかなで入力してください",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            buildAnnotatedString {
                                charStates(target, input).forEachIndexed { i, state ->
                                    val style = when (state) {
                                        CharState.CORRECT -> SpanStyle(color = correctColor)
                                        CharState.WRONG -> SpanStyle(color = wrongColor, background = wrongColor.copy(alpha = 0.12f))
                                        CharState.PENDING -> SpanStyle(color = pendingColor)
                                    }
                                    withStyle(style) { append(target[i]) }
                                }
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        OutlinedTextField(
                            value = input,
                            onValueChange = ::handleChange,
                            placeholder = { Text("ここに入力") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                }
            }

            if (average != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("平均速度(直近${results.size}回)", style = MaterialTheme.typography.bodyMedium, color = pendingColor)
                            Text("$average WPM", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("記録", style = MaterialTheme.typography.labelLarge, color = pendingColor)
                            results.forEach { r ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text("${r.wpm} WPM", modifier = Modifier.weight(1f))
                                    Text("正確率 ${r.accuracy}%", color = pendingColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
