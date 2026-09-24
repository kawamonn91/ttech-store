package com.ttech.stretchreminder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.stretchreminder.data.StretchStore
import com.ttech.stretchreminder.data.defaultStretchState
import com.ttech.stretchreminder.domain.isDue
import com.ttech.stretchreminder.domain.nextReminderAt
import com.ttech.stretchreminder.domain.normalizeInterval
import com.ttech.stretchreminder.domain.remainingMin
import com.ttech.stretchreminder.domain.suggestion
import com.ttech.stretchreminder.domain.todayCount
import com.ttech.stretchreminder.reminder.scheduleReminder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StretchReminderScreen() {
    val context = LocalContext.current
    val store = remember { StretchStore(context) }
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState(initial = defaultStretchState())

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var intervalText by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        store.ensureInitialized()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // 間隔や最後の完了時刻が変わるたびに、次の通知を予約し直す。
    LaunchedEffect(state.lastDoneAt, state.intervalMin) {
        scheduleReminder(context, nextReminderAt(state))
    }

    val today = LocalDate.now().toString()
    val count = todayCount(state, today)
    val due = isDue(state, now)
    val remaining = remainingMin(state, now)

    fun markDone() {
        val doneAt = System.currentTimeMillis()
        val doneToday = LocalDate.now().toString()
        scope.launch { store.markDone(doneAt, doneToday) }
    }

    fun onIntervalChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(4)
        intervalText = digits
        val minutes = normalizeInterval(digits.toIntOrNull())
        scope.launch { store.setInterval(minutes) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("姿勢改善ストレッチリマインダー") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    border = if (due) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (due) "ストレッチの時間です" else "次のストレッチまで",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (due) "今すぐ" else "あと${remaining}分",
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        Text(suggestion(count), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = ::markDone, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text("ストレッチ完了")
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "リマインド間隔(分)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = intervalText ?: state.intervalMin.toString(),
                            onValueChange = ::onIntervalChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今日の実施回数", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${count}回", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            item {
                Text(
                    "※ 次のストレッチの時間になると通知でお知らせします(通知の許可が必要です)。" +
                        "時刻は多少ずれることがあります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
