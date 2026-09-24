package com.ttech.stretchreminder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ttech.stretchreminder.data.StretchStore
import com.ttech.stretchreminder.domain.countOn
import com.ttech.stretchreminder.domain.isDue
import com.ttech.stretchreminder.domain.nextReminderAt
import com.ttech.stretchreminder.domain.parseInterval
import com.ttech.stretchreminder.domain.remainingMinutes
import com.ttech.stretchreminder.domain.suggestion
import com.ttech.stretchreminder.reminder.StretchReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StretchReminderScreen() {
    val context = LocalContext.current
    val store = remember { StretchStore(context) }
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState(initial = null)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        store.initLastDoneIfMissing(System.currentTimeMillis())
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    var notificationsEnabled by remember { mutableStateOf(notificationsGranted()) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled = granted
    }

    // 前回の実施時刻・間隔が変わるたびに、次の通知を予約し直す
    LaunchedEffect(state?.lastDoneAt, state?.intervalMin) {
        val s = state ?: return@LaunchedEffect
        StretchReminderScheduler.schedule(context, nextReminderAt(s.lastDoneAt, s.intervalMin))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("ストレッチリマインダー") }) }) { padding ->
        val s = state ?: return@Scaffold
        val today = LocalDate.now().toString()
        val todayCount = s.completedToday.countOn(today)
        val due = isDue(now, s.lastDoneAt, s.intervalMin)
        val remaining = remainingMinutes(now, s.lastDoneAt, s.intervalMin)

        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                OutlinedCard(
                    Modifier.fillMaxWidth(),
                    border = BorderStroke(if (due) 2.dp else 1.dp, if (due) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (due) "ストレッチの時間です" else "次のストレッチまで",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (due) "今すぐ" else "あと${remaining}分",
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        Text(suggestion(todayCount), textAlign = TextAlign.Center)
                        Button(
                            onClick = { scope.launch { store.markDone(System.currentTimeMillis(), today) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text("ストレッチ完了") }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    IntervalField(s.intervalMin) { minutes -> scope.launch { store.setInterval(minutes) } }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今日の実施回数", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${todayCount}回", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            if (!notificationsEnabled) {
                item {
                    OutlinedButton(
                        onClick = { requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("時間になったら通知で知らせる") }
                }
            }

            item {
                Text(
                    if (notificationsEnabled) {
                        "※ アプリを閉じていても、次のストレッチの時刻に通知でお知らせします(端末の省電力設定により数分遅れることがあります)。"
                    } else {
                        "※ 通知を許可すると、アプリを閉じていても次のストレッチの時刻にお知らせします。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 間隔の入力欄。入力途中(空欄や「1」など)を保つため、確定値の保存は範囲内に収めてから行う。 */
@Composable
private fun IntervalField(intervalMin: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(intervalMin.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() }.take(3)
            if (text.isNotEmpty()) onChange(parseInterval(text))
        },
        label = { Text("リマインド間隔(分、10〜240)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}
