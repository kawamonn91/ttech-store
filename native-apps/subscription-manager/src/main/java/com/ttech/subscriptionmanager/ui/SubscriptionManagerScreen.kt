package com.ttech.subscriptionmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.subscriptionmanager.data.SubscriptionStore
import com.ttech.subscriptionmanager.domain.Cycle
import com.ttech.subscriptionmanager.domain.DUE_SOON_DAYS
import com.ttech.subscriptionmanager.domain.Subscription
import com.ttech.subscriptionmanager.domain.addSorted
import com.ttech.subscriptionmanager.domain.buildSubscription
import com.ttech.subscriptionmanager.domain.daysUntil
import com.ttech.subscriptionmanager.domain.monthlyTotal
import com.ttech.subscriptionmanager.domain.remainingLabel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionManagerScreen() {
    val context = LocalContext.current
    val store = remember { SubscriptionStore(context) }
    val scope = rememberCoroutineScope()
    val subs by store.subscriptions.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf(Cycle.MONTHLY) }
    var nextPaymentDate by remember { mutableStateOf(LocalDate.now()) }

    fun addSub() {
        val sub = buildSubscription(UUID.randomUUID().toString(), name, amount, cycle, nextPaymentDate.toString()) ?: return
        scope.launch { store.update { it.addSorted(sub) } }
        name = ""; amount = ""
    }

    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val today = LocalDate.now()

    Scaffold(topBar = { TopAppBar(title = { Text("サブスク管理") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("月あたりの支払い合計(概算)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${numberFormat.format(subs.monthlyTotal())}円", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("サービス名") },
                            placeholder = { Text("例: 動画配信サービス") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { v -> amount = v.filter { it.isDigit() }.take(9) },
                                label = { Text("金額(円)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            SingleChoiceSegmentedButtonRow(Modifier.weight(1f).padding(top = 8.dp)) {
                                Cycle.entries.forEachIndexed { index, c ->
                                    SegmentedButton(
                                        selected = cycle == c,
                                        onClick = { cycle = c },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Cycle.entries.size),
                                    ) { Text(c.label) }
                                }
                            }
                        }
                        DateField("次回の支払日", nextPaymentDate) { nextPaymentDate = it }
                        Button(
                            onClick = ::addSub,
                            enabled = name.isNotBlank() && amount.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("追加する") }
                    }
                }
            }

            item {
                Text(
                    "登録中のサブスク(${subs.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (subs.isEmpty()) {
                item { Text("まだ登録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(subs, key = Subscription::id) { s ->
                val remaining = daysUntil(s.nextPaymentDate, today)
                val dueSoon = remaining <= DUE_SOON_DAYS
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.name} ・ ${numberFormat.format(s.amount)}円/${s.cycle.unit}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "次回 ${s.nextPaymentDate}(${remainingLabel(remaining)})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (dueSoon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (dueSoon) FontWeight.Medium else null,
                            )
                        }
                        TextButton(onClick = { scope.launch { store.remove(s.id) } }) { Text("解約/削除") }
                    }
                }
            }
        }
    }
}

private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

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
                    state.selectedDateMillis?.let { onChange(it.toLocalDate()) }
                    showPicker = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}
