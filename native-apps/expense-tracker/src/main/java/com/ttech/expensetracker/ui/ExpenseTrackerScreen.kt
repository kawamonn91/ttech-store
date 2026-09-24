package com.ttech.expensetracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.expensetracker.data.ExpenseStore
import com.ttech.expensetracker.domain.Category
import com.ttech.expensetracker.domain.Expense
import com.ttech.expensetracker.domain.filterByMonth
import com.ttech.expensetracker.domain.monthKeyOf
import com.ttech.expensetracker.domain.totalAmount
import com.ttech.expensetracker.domain.totalsByCategory
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

private fun Int.toYenString(): String = "%,d円".format(Locale.JAPAN, this)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerScreen() {
    val context = LocalContext.current
    val store = remember { ExpenseStore(context) }
    val scope = rememberCoroutineScope()
    val expenses by store.expenses.collectAsState(initial = emptyList())

    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(Category.FOOD) }
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }

    fun addExpense() {
        val amountN = amount.toIntOrNull()
        if (amountN == null || amountN == 0) return
        val expense = Expense(id = UUID.randomUUID().toString(), date = date, category = category, amount = amountN, memo = memo.trim())
        scope.launch { store.add(expense) }
        amount = ""; memo = ""
    }

    val thisMonth = monthKeyOf(LocalDate.now().toString())
    val monthExpenses = expenses.filterByMonth(thisMonth)
    val monthTotal = monthExpenses.totalAmount()
    val byCategory = monthExpenses.totalsByCategory()

    Scaffold(topBar = { TopAppBar(title = { Text("シンプル家計簿") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("今月の支出合計", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(monthTotal.toYenString(), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                                Text(date)
                            }
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = { Text("金額(円)") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text("カテゴリ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Category.entries.forEach { c ->
                                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                            }
                        }
                        OutlinedTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            label = { Text("メモ(任意)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::addExpense, enabled = amount.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("記録する")
                        }
                    }
                }
            }

            if (byCategory.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("今月のカテゴリ別内訳", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            byCategory.forEach { row ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(row.category.label)
                                    Text(row.total.toYenString())
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "記録一覧(${expenses.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expenses.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(expenses, key = Expense::id) { expense ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("${expense.category.label} ・ ${expense.amount.toYenString()}", style = MaterialTheme.typography.bodyMedium)
                            val sub = expense.date + if (expense.memo.isNotEmpty()) " ・ ${expense.memo}" else ""
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { scope.launch { store.remove(expense.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                .getOrDefault(System.currentTimeMillis()),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}
