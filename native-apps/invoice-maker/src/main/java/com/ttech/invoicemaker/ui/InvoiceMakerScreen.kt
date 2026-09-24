package com.ttech.invoicemaker.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
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
import com.ttech.invoicemaker.data.InvoiceStore
import com.ttech.invoicemaker.data.printHtml
import com.ttech.invoicemaker.domain.DOC_TYPES
import com.ttech.invoicemaker.domain.InvoiceData
import com.ttech.invoicemaker.domain.LineItem
import com.ttech.invoicemaker.domain.addItem
import com.ttech.invoicemaker.domain.buildInvoiceHtml
import com.ttech.invoicemaker.domain.emptyInvoice
import com.ttech.invoicemaker.domain.formatNumber
import com.ttech.invoicemaker.domain.removeItem
import com.ttech.invoicemaker.domain.totals
import com.ttech.invoicemaker.domain.updateItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InvoiceMakerScreen() {
    val context = LocalContext.current
    val store = remember { InvoiceStore(context) }
    val scope = rememberCoroutineScope()

    // 入力のたびに DataStore の Flow から読み戻すとカーソル位置が飛ぶため、
    // 起動時に一度だけ読み込み、以降は画面側の状態を正として保存だけ行う。
    var data by remember { mutableStateOf<InvoiceData?>(null) }
    LaunchedEffect(Unit) {
        data = store.data.first() ?: emptyInvoice(LocalDate.now().toString(), UUID.randomUUID().toString())
    }
    // 印刷中に WebView が破棄されないよう参照を保持する
    var printingWebView by remember { mutableStateOf<WebView?>(null) }

    fun update(next: InvoiceData) {
        data = next
        scope.launch { store.save(next) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("見積書・請求書かんたん作成") }) }) { padding ->
        val current = data ?: return@Scaffold
        val totals = current.totals()
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            DOC_TYPES.forEachIndexed { index, t ->
                                SegmentedButton(
                                    selected = current.docType == t,
                                    onClick = { update(current.copy(docType = t)) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DOC_TYPES.size),
                                ) { Text(t) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            OutlinedTextField(
                                value = current.docNumber,
                                onValueChange = { update(current.copy(docNumber = it)) },
                                label = { Text("文書番号") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            DateField("発行日", current.issueDate, Modifier.weight(1f)) { update(current.copy(issueDate = it)) }
                        }
                        OutlinedTextField(
                            value = current.clientName,
                            onValueChange = { update(current.copy(clientName = it)) },
                            label = { Text("宛先(お客様名)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = current.issuerName,
                            onValueChange = { update(current.copy(issuerName = it)) },
                            label = { Text("発行者名(あなたの名前・屋号)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Text("項目", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(current.items, key = LineItem::id) { item ->
                LineItemRow(
                    item = item,
                    onChange = { transform -> data?.let { update(it.updateItem(item.id, transform)) } },
                    onRemove = { data?.let { update(it.removeItem(item.id)) } },
                )
            }

            item {
                OutlinedButton(onClick = { update(current.addItem(UUID.randomUUID().toString())) }, modifier = Modifier.fillMaxWidth()) {
                    Text("項目を追加")
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("消費税率(%)", current.taxPercent, Modifier.width(140.dp)) { update(current.copy(taxPercent = it)) }
                        OutlinedTextField(
                            value = current.notes,
                            onValueChange = { update(current.copy(notes = it)) },
                            label = { Text("備考") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TotalRow("小計", "${formatNumber(totals.subtotal)}円")
                        TotalRow("消費税(${formatNumber(current.taxPercent)}%)", "${formatNumber(totals.tax)}円")
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        TotalRow("合計", "${formatNumber(totals.total)}円", bold = true)
                    }
                }
            }

            item {
                Button(
                    onClick = { printingWebView = printHtml(context, buildInvoiceHtml(current), current.docType) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("印刷 / PDFとして保存") }
            }
        }
    }
}

@Composable
private fun LineItemRow(item: LineItem, onChange: ((LineItem) -> LineItem) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { v -> onChange { it.copy(name = v) } },
                    label = { Text("品目") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "項目を削除") }
            }
            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField("数量", item.qty, Modifier.weight(1f)) { v -> onChange { it.copy(qty = v) } }
                NumberField("単価", item.unitPrice, Modifier.weight(1.4f)) { v -> onChange { it.copy(unitPrice = v) } }
                Text("${formatNumber(item.amount)}円", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** 数値入力欄。入力途中の文字列(「1.」など)を保つため、表示は画面側の文字列を正とする。 */
@Composable
private fun NumberField(label: String, value: Double, modifier: Modifier = Modifier, onChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(formatPlain(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() || it == '.' }.take(10)
            onChange(text.toDoubleOrNull() ?: 0.0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun formatPlain(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun TotalRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, dateIso: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(dateIso) }.getOrElse { LocalDate.now() }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(date.toString())
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
