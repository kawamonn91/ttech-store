package com.ttech.receipttracker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.common.share.shareTextFile
import com.ttech.receipttracker.data.ReceiptStore
import com.ttech.receipttracker.data.deleteReceiptImage
import com.ttech.receipttracker.data.loadReceiptThumbnail
import com.ttech.receipttracker.data.saveReceiptImage
import com.ttech.receipttracker.domain.CATEGORIES
import com.ttech.receipttracker.domain.Receipt
import com.ttech.receipttracker.domain.buildReceipt
import com.ttech.receipttracker.domain.detailLabel
import com.ttech.receipttracker.domain.toCsv
import com.ttech.receipttracker.domain.total
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReceiptTrackerScreen() {
    val context = LocalContext.current
    val store = remember { ReceiptStore(context) }
    val scope = rememberCoroutineScope()
    val receipts by store.receipts.collectAsState(initial = emptyList())

    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    var memo by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageUri = uri
    }
    LaunchedEffect(imageUri) {
        val uri = imageUri
        preview = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri).use { input ->
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                }
            }.getOrNull()
        }
    }

    fun addReceipt() {
        val id = UUID.randomUUID().toString()
        // 画像の保存は時間がかかるので、入力値はここで確定させてから非同期で保存する
        val uri = imageUri
        val date = LocalDate.now().toString()
        val amountText = amount
        val selectedCategory = category
        val memoText = memo
        if (buildReceipt(id, date, amountText, selectedCategory, memoText, null) == null) return
        scope.launch {
            val imageFile = uri?.let { withContext(Dispatchers.IO) { runCatching { saveReceiptImage(context, it, id) }.getOrNull() } }
            buildReceipt(id, date, amountText, selectedCategory, memoText, imageFile)?.let { store.add(it) }
        }
        amount = ""; memo = ""; imageUri = null
    }

    fun removeReceipt(receipt: Receipt) {
        scope.launch {
            store.remove(receipt.id)
            receipt.imageFile?.let { withContext(Dispatchers.IO) { deleteReceiptImage(context, it) } }
        }
    }

    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    Scaffold(topBar = { TopAppBar(title = { Text("経費レシート記録") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("記録済み合計", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${numberFormat.format(receipts.total())}円", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("レシート画像(任意)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Text(if (imageUri == null) "画像を選ぶ" else "画像を選び直す")
                            }
                            if (imageUri != null) TextButton(onClick = { imageUri = null }) { Text("外す") }
                        }
                        preview?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "レシートプレビュー",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.heightIn(max = 128.dp),
                            )
                        }
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { v -> amount = v.filter { it.isDigit() }.take(9) },
                            label = { Text("金額(円)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("カテゴリ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CATEGORIES.forEach { c ->
                                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                            }
                        }
                        OutlinedTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            label = { Text("メモ") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::addReceipt, enabled = amount.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("記録する")
                        }
                    }
                }
            }

            if (receipts.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            shareTextFile(
                                context = context,
                                content = toCsv(receipts),
                                filename = "receipts.csv",
                                mimeType = "text/csv",
                                chooserTitle = "CSVを保存・共有",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("CSVとして出力") }
                }
            }

            item {
                Text(
                    "記録一覧(${receipts.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (receipts.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(receipts, key = Receipt::id) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        r.imageFile?.let { ReceiptThumbnail(it) }
                        Column(Modifier.weight(1f)) {
                            Text("${r.category} ・ ${numberFormat.format(r.amount)}円", style = MaterialTheme.typography.titleMedium)
                            Text(r.detailLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { removeReceipt(r) }) { Text("削除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptThumbnail(name: String) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, name) {
        value = withContext(Dispatchers.IO) { loadReceiptThumbnail(context, name) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "レシート画像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp),
        )
    }
}
