package com.ttech.pdftoolkit.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.pdftoolkit.data.PdfOps
import com.ttech.pdftoolkit.data.sharePdf
import com.ttech.pdftoolkit.data.sharedOutputFile
import com.ttech.pdftoolkit.domain.canExtract
import com.ttech.pdftoolkit.domain.canMerge
import com.ttech.pdftoolkit.domain.parsePageRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Mode(val label: String) { MERGE("結合"), SPLIT("分割") }

private data class PickedPdf(val uri: Uri, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolkitScreen() {
    var mode by remember { mutableStateOf(Mode.MERGE) }

    Scaffold(topBar = { TopAppBar(title = { Text("PDF結合・分割") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Mode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Mode.entries.size),
                        ) { Text(m.label) }
                    }
                }
            }
            item { if (mode == Mode.MERGE) MergePanel() else SplitPanel() }
        }
    }
}

@Composable
private fun MergePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(listOf<PickedPdf>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            files = uris.map { PickedPdf(it, PdfOps.displayName(context, it)) }
            error = null
        }
    }

    fun merge() {
        if (!canMerge(files.size) || busy) return
        // launch の中で状態を読まず、先に値を確定させる。
        val uris = files.map { it.uri }
        busy = true
        error = null
        scope.launch {
            val output = sharedOutputFile(context, "merged.pdf")
            val result = runCatching { withContext(Dispatchers.IO) { PdfOps.merge(context, uris, output) } }
            busy = false
            result
                .onSuccess { sharePdf(context, output, "結合したPDFを共有・保存") }
                .onFailure { error = it.message ?: "結合に失敗しました" }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "結合したいPDFを2つ以上、順番に選択してください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("PDFを選択(複数可)") }
            files.forEachIndexed { i, f -> Text("${i + 1}. ${f.name}") }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::merge, enabled = canMerge(files.size) && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "結合中..." else "結合して共有・保存")
            }
        }
    }
}

@Composable
private fun SplitPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<PickedPdf?>(null) }
    var pageCount by remember { mutableStateOf<Int?>(null) }
    var range by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = PickedPdf(uri, PdfOps.displayName(context, uri))
        file = picked
        pageCount = null
        error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { PdfOps.pageCount(context, picked.uri) } }
                .onSuccess { pageCount = it }
                .onFailure { error = it.message ?: "PDFを読み込めませんでした" }
        }
    }

    fun extract() {
        val source = file ?: return
        val total = pageCount ?: return
        if (!canExtract(true, range) || busy) return
        val indices = parsePageRange(range, total)
        if (indices.isEmpty()) {
            error = "有効なページ番号を入力してください"
            return
        }
        busy = true
        error = null
        scope.launch {
            val output = sharedOutputFile(context, "split.pdf")
            val result = runCatching { withContext(Dispatchers.IO) { PdfOps.extract(context, source.uri, indices, output) } }
            busy = false
            result
                .onSuccess { sharePdf(context, output, "抽出したPDFを共有・保存") }
                .onFailure { error = it.message ?: "分割に失敗しました" }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "分割したいPDFを選択してください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("PDFを選択") }
            file?.let { Text(it.name) }
            pageCount?.let { total ->
                Text("全${total}ページ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = range,
                    onValueChange = { range = it },
                    label = { Text("抽出するページ番号") },
                    placeholder = { Text("1-3,5") },
                    supportingText = { Text("例: 1-3,5 で1〜3ページと5ページ目を抽出") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = ::extract,
                enabled = canExtract(file != null && pageCount != null, range) && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "処理中..." else "抽出して共有・保存") }
        }
    }
}
