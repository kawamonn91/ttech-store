package com.ttech.typingpractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ttech.typingpractice.domain.TypingResult
import com.ttech.typingpractice.domain.averageWpm
import com.ttech.typingpractice.domain.evaluate
import com.ttech.typingpractice.domain.pickSentence
import com.ttech.typingpractice.domain.withNewResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingPracticeScreen() {
    var target by remember { mutableStateOf(pickSentence()) }
    var input by remember { mutableStateOf("") }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var results by remember { mutableStateOf(listOf<TypingResult>()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(target) { focusRequester.requestFocus() }

    fun onInputChange(value: String) {
        val now = System.currentTimeMillis()
        val started = startedAt ?: if (value.isNotEmpty()) now else null
        startedAt = started
        input = value

        val result = evaluate(value, target, started, now) ?: return
        results = results.withNewResult(result)
        target = pickSentence(exclude = target)
        input = ""
        startedAt = null
    }

    val okColor = MaterialTheme.colorScheme.primary
    val ngColor = Color(0xFFDC2626)
    val ngBackground = Color(0xFFFEE2E2)
    val pendingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = buildAnnotatedString {
        target.forEachIndexed { i, ch ->
            val style = when {
                i >= input.length -> SpanStyle(color = pendingColor)
                input[i] == ch -> SpanStyle(color = okColor)
                else -> SpanStyle(color = ngColor, background = ngBackground)
            }
            withStyle(style) { append(ch) }
        }
    }
    val avg = results.averageWpm()

    Scaffold(topBar = { TopAppBar(title = { Text("タイピング練習") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "この文をかなで入力してください",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(annotated, style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = input,
                            onValueChange = ::onInputChange,
                            placeholder = { Text("ここに入力") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                }
            }

            if (avg != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "平均速度(直近${results.size}回)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("$avg WPM", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("記録", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            results.forEach { r ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${r.wpm} WPM")
                                    Text("正確率 ${r.accuracy}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
