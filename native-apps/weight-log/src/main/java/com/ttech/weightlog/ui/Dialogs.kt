package com.ttech.weightlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.weightlog.domain.Limits
import com.ttech.weightlog.domain.Profile
import com.ttech.weightlog.domain.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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

private fun numberField(value: String, max: Int) = value.filter { it.isDigit() || it == '.' }.take(max)

/** 体重の記録の追加・編集。[entry] が null なら新規(日付は今日) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEditDialog(entry: WeightEntry?, today: LocalDate, existingDates: Set<String>, onSave: (WeightEntry) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var date by rememberSaveable { mutableStateOf(entry?.date ?: today.toString()) }
    var weight by rememberSaveable { mutableStateOf(entry?.weightKg?.toString() ?: "") }
    var bodyFat by rememberSaveable { mutableStateOf(entry?.bodyFatPercent?.toString() ?: "") }
    var memo by rememberSaveable { mutableStateOf(entry?.memo ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    val d = LocalDate.parse(date)
    val overwritesOther = entry == null && date in existingDates

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "体重を記録" else "記録を編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${d.year}/${d.monthValue}/${d.dayOfMonth}")
                }
                if (overwritesOther) {
                    Text("この日はすでに記録があります。保存すると上書きします。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = numberField(it, 6) }, label = { Text("体重(kg)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = bodyFat, onValueChange = { bodyFat = numberField(it, 5) }, label = { Text("体脂肪率(%、任意)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = memo, onValueChange = { memo = it.take(Limits.MAX_MEMO) }, label = { Text("メモ") }, singleLine = false, minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = weight.toDoubleOrNull() != null,
                onClick = {
                    val base = entry ?: WeightEntry(id = UUID.randomUUID().toString(), date = date, weightKg = 0.0)
                    onSave(base.copy(date = date, weightKg = weight.toDouble(), bodyFatPercent = bodyFat.toDoubleOrNull(), memo = memo.trim()))
                },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("削除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        },
    )
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = { date = Instant.ofEpochMilli(state.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate().toString(); pickDate = false },
                ) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}

/** 身長・目標体重・目標日の設定 */
@Composable
fun ProfileEditDialog(profile: Profile, onSave: (Profile) -> Unit, onDismiss: () -> Unit) {
    var height by rememberSaveable { mutableStateOf(profile.heightCm?.toString() ?: "") }
    var goal by rememberSaveable { mutableStateOf(profile.goalWeightKg?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("身長・目標") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = height, onValueChange = { height = numberField(it, 5) }, label = { Text("身長(cm、任意。BMIの計算に使います)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = goal, onValueChange = { goal = numberField(it, 6) }, label = { Text("目標体重(kg、任意)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(profile.copy(heightCm = height.toDoubleOrNull(), goalWeightKg = goal.toDoubleOrNull())) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
