package com.ttech.ideamemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.ideamemo.domain.Category
import com.ttech.ideamemo.domain.Idea
import com.ttech.ideamemo.domain.Limits
import com.ttech.ideamemo.domain.Status
import java.util.UUID

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true, max: Int = Limits.MAX_SHORT, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(max)) },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** アイデアの追加・編集。[idea] が null なら新規 */
@Composable
fun IdeaEditDialog(idea: Idea?, onSave: (Idea) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(idea?.title ?: "") }
    var oneLiner by rememberSaveable { mutableStateOf(idea?.oneLiner ?: "") }
    var memo by rememberSaveable { mutableStateOf(idea?.memo ?: "") }
    var category by rememberSaveable { mutableStateOf(idea?.category ?: Category.OTHER) }
    var status by rememberSaveable { mutableStateOf(idea?.status ?: Status.IDEA) }
    var priority by rememberSaveable { mutableIntStateOf(idea?.priority ?: 2) }
    var reference by rememberSaveable { mutableStateOf(idea?.reference ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (idea == null) "アイデアを追加" else "アイデアを編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(title, { title = it }, "タイトル(例: 習慣トラッカー)", max = Limits.MAX_TITLE)
                Field(oneLiner, { oneLiner = it }, "一言で言うと(触れ込み)")
                Text("カテゴリ", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Category.entries) { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) }) }
                }
                Text("状態", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Status.entries) { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("作りたい度", style = MaterialTheme.typography.bodyMedium)
                    StarInput(priority, { priority = it })
                }
                Field(reference, { reference = it }, "きっかけ・参考(既存アプリ・URLなど)")
                Field(memo, { memo = it }, "メモ", singleLine = false, max = Limits.MAX_TEXT)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val base = idea ?: Idea(id = UUID.randomUUID().toString(), title = "")
                    onSave(
                        base.copy(
                            title = title.trim(), oneLiner = oneLiner.trim(), memo = memo.trim(),
                            category = category, status = status, priority = priority, reference = reference.trim(),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
