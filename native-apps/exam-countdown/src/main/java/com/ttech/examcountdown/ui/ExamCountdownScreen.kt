package com.ttech.examcountdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.unit.dp
import com.ttech.examcountdown.data.ExamStore
import com.ttech.examcountdown.domain.StudySession
import com.ttech.examcountdown.domain.daysUntil
import com.ttech.examcountdown.domain.formatElapsed
import com.ttech.examcountdown.domain.formatHoursAndMinutes
import com.ttech.examcountdown.domain.totalMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExamCountdownScreen() {
    val context = LocalContext.current
    val store = remember { ExamStore(context) }
    val scope = rememberCoroutineScope()
    val examName by store.examName.collectAsState(initial = "")
    val examDate by store.examDate.collectAsState(initial = LocalDate.now().toString())
    val sessions by store.sessions.collectAsState(initial = emptyList())

    var showDatePicker by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var elapsedSec by remember { mutableStateOf(0L) }

    LaunchedEffect(running, startedAt) {
        val start = startedAt
        if (!running || start == null) return@LaunchedEffect
        while (true) {
            elapsedSec = (System.currentTimeMillis() - start) / 1000
            delay(1000)
        }
    }

    fun toggleTimer() {
        if (running) {
            val minutes = Math.round(elapsedSec / 60.0).toInt()
            if (minutes > 0) {
                val session = StudySession(id = UUID.randomUUID().toString(), date = LocalDate.now().toString(), minutes = minutes)
                scope.launch { store.addSession(session) }
            }
            running = false
            startedAt = null
            elapsedSec = 0
        } else {
            startedAt = System.currentTimeMillis()
            running = true
        }
    }

    val remaining = daysUntil(examDate, LocalDate.now().toString())
    val total = totalMinutes(sessions)

    Scaffold(topBar = { TopAppBar(title = { Text("資格試験カウントダウン") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text("試験名", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = examName,
                                onValueChange = { scope.launch { store.setExamName(it) } },
                                placeholder = { Text("例: 宅地建物取引士試験") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column {
                            Text("試験日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(examDate)
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${examName.ifBlank { "試験" }}まで",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (remaining >= 0) "残り${remaining}日" else "${-remaining}日経過",
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("学習タイマー", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatElapsed(elapsedSec),
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        Button(onClick = ::toggleTimer, modifier = Modifier.fillMaxWidth()) {
                            Text(if (running) "終了して記録する" else "学習を開始する")
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("累計学習時間", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatHoursAndMinutes(total), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(examDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                .getOrDefault(System.currentTimeMillis()),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val newDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        scope.launch { store.setExamDate(newDate) }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}
