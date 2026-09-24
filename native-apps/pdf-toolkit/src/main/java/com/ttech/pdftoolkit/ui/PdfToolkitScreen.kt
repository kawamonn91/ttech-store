package com.ttech.pdftoolkit.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.ttech.pdftoolkit.data.PdfFiles
import com.ttech.pdftoolkit.data.failureMessage
import com.ttech.pdftoolkit.domain.parseRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Mode(val label: String) { MERGE("結合"), SPLIT("分割") }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PdfToolkitScreen() {
    val context = LocalContext.current
    val files = remember { PdfFiles(context) }
    var mode by remember { mutableStateOf(Mode.MERGE) }

    Scaffold(topBar = { TopAppBar(title = { Text("PDF結合・分割") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Mode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Mode.entries.size),
                        ) { Text(m.label) }
                    }
                }
            }
            item {
                when (mode) {
                    Mode.MERGE -> MergePanel(files)
                    Mode.SPLIT -> SplitPanel(files)
                }
            }
        }
    }
}

private data class PickedFile(val uri: Uri, val name: String)

@Composable
private fun MergePanel(files: PdfFiles) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(emptyList<PickedFile>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            picked = withContext(Dispatchers.IO) { uris.map { PickedFile(it, files.displayName(it)) } }
            message = null; error = null
        }
    }
    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        busy = true; message = null; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { files.merge(picked.map { it.uri }, destination) } }
                .onSuccess { message = "結合したPDFを保存しました" }
                .onFailure { error = failureMessage(it, "結合に失敗しました") }
            busy = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "結合したいPDFを2つ以上、順番に選択してください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { pickFiles.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (picked.isEmpty()) "PDFを選ぶ" else "PDFを選び直す")
            }
            picked.forEachIndexed { i, f -> Text("${i + 1}. ${f.name}", style = MaterialTheme.typography.bodyMedium) }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = { saveTo.launch("merged.pdf") }, enabled = picked.size >= 2 && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "結合中..." else "結合して保存")
            }
        }
    }
}

@Composable
private fun SplitPanel(files: PdfFiles) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var pageCount by remember { mutableStateOf<Int?>(null) }
    var range by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = null; error = null; pageCount = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { PickedFile(uri, files.displayName(uri)) to files.pageCount(uri) } }
                .onSuccess { (file, count) -> picked = file; pageCount = count }
                .onFailure { picked = null; error = failureMessage(it, "PDFを読み込めませんでした") }
        }
    }
    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
        val source = picked ?: return@rememberLauncherForActivityResult
        if (destination == null) return@rememberLauncherForActivityResult
        val indices = parseRange(range, pageCount ?: 0)
        busy = true; message = null; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { files.extract(source.uri, indices, destination) } }
                .onSuccess { message = "抽出したPDFを保存しました" }
                .onFailure { error = failureMessage(it, "分割に失敗しました") }
            busy = false
        }
    }

    fun extract() {
        if (parseRange(range, pageCount ?: 0).isEmpty()) {
            error = "有効なページ番号を入力してください"
            return
        }
        saveTo.launch("split.pdf")
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("分割したいPDFを選択してください", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { pickFile.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(picked?.name ?: "PDFを選ぶ")
            }
            pageCount?.let { count ->
                Text("全${count}ページ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            Button(
                onClick = ::extract,
                enabled = picked != null && pageCount != null && range.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "処理中..." else "抽出して保存") }
        }
    }
}
