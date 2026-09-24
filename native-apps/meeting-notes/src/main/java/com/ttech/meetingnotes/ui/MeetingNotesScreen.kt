package com.ttech.meetingnotes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ttech.common.share.shareTextFile
import com.ttech.meetingnotes.data.NotesFormStore
import com.ttech.meetingnotes.domain.NotesForm
import com.ttech.meetingnotes.domain.buildFormattedText
import com.ttech.meetingnotes.domain.textFilename
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MeetingNotesScreen() {
    val context = LocalContext.current
    val store = remember { NotesFormStore(context) }
    val scope = rememberCoroutineScope()

    // 入力のたびに DataStore の Flow から読み戻すとカーソル位置が飛ぶため、
    // 起動時に一度だけ読み込み、以降は画面側の状態を正として保存だけ行う。
    var form by remember { mutableStateOf<NotesForm?>(null) }
    LaunchedEffect(Unit) {
        form = store.form.first() ?: NotesForm(date = LocalDate.now().toString())
    }

    fun update(next: NotesForm) {
        form = next
        scope.launch { store.save(next) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("議事録テンプレ整形") }) }) { padding ->
        val current = form ?: return@Scaffold
        val formatted = buildFormattedText(current)
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField("日付", current.date) { update(current.copy(date = it)) }
                        FormField("会議名", current.title) { update(current.copy(title = it)) }
                        FormField("参加者(カンマ区切り)", current.attendees) { update(current.copy(attendees = it)) }
                        FormField("議題", current.agenda, multiline = true) { update(current.copy(agenda = it)) }
                        FormField("決定事項", current.decisions, multiline = true) { update(current.copy(decisions = it)) }
                        FormField("ToDo / アクションアイテム", current.actionItems, multiline = true) {
                            update(current.copy(actionItems = it))
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { copyToClipboard(context, formatted) }, modifier = Modifier.weight(1f)) {
                        Text("コピー")
                    }
                    OutlinedButton(
                        onClick = {
                            shareTextFile(
                                context = context,
                                content = formatted,
                                filename = textFilename(current),
                                chooserTitle = "議事録を保存・共有",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(".txtで保存") }
                }
                TextButton(
                    onClick = { update(NotesForm(date = LocalDate.now().toString())) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("フォームをクリア") }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("プレビュー", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("議事録", text))
    Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
}

@Composable
private fun FormField(label: String, value: String, multiline: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, dateIso: String, onChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(dateIso) }.getOrElse { LocalDate.now() }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(date.toString(), modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it.toLocalDate().toString()) }
                    showPicker = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}
