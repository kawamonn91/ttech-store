package com.ttech.kidsallowance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.ttech.kidsallowance.data.TransactionStore
import com.ttech.kidsallowance.domain.Transaction
import com.ttech.kidsallowance.domain.TransactionType
import com.ttech.kidsallowance.domain.balance
import com.ttech.kidsallowance.domain.buildTransaction
import com.ttech.kidsallowance.domain.formatSignedYen
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun KidsAllowanceScreen() {
    val context = LocalContext.current
    val store = remember { TransactionStore(context) }
    val scope = rememberCoroutineScope()
    val transactions by store.transactions.collectAsState(initial = emptyList())

    var type by remember { mutableStateOf(TransactionType.IN) }
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    fun addTransaction() {
        val t = buildTransaction(UUID.randomUUID().toString(), LocalDate.now().toString(), label, amount, type) ?: return
        scope.launch { store.add(t) }
        label = ""; amount = ""
    }

    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    Scaffold(topBar = { TopAppBar(title = { Text("お小遣い帳") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今の残高", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${numberFormat.format(transactions.balance())}円", style = MaterialTheme.typography.displaySmall)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(TransactionType.IN to "もらった", TransactionType.OUT to "つかった").forEachIndexed { index, (t, text) ->
                                SegmentedButton(
                                    selected = type == t,
                                    onClick = { type = t },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                ) { Text(text) }
                            }
                        }
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("なにに?") },
                            placeholder = { Text("例: おこづかい、おかし") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { v -> amount = v.filter { it.isDigit() }.take(9) },
                            label = { Text("金額(円)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = ::addTransaction,
                            enabled = amount.isNotEmpty() && label.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("きろくする") }
                    }
                }
            }

            item {
                Text("きろく一覧", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (transactions.isEmpty()) {
                item { Text("まだきろくがありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(transactions, key = Transaction::id) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${t.date} ・ ${t.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            formatSignedYen(t.amount),
                            color = if (t.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = { scope.launch { store.remove(t.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}
