package com.ttech.weightlog.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ttech.weightlog.data.WeightRepository
import com.ttech.weightlog.domain.WeightFile
import com.ttech.weightlog.domain.computeStats
import com.ttech.weightlog.domain.sampleEntries
import com.ttech.weightlog.share.shareBackup
import com.ttech.weightlog.share.shareEntries
import com.ttech.weightlog.share.shareSummary
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightApp(repository: WeightRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by repository.loaded.collectAsState()
    val entries by repository.entries.collectAsState()
    val profile by repository.profile.collectAsState()
    val today = remember { LocalDate.now() }

    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun newId() = UUID.randomUUID().toString()

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { readLimited(it, WeightFile.MAX_BYTES + 1) } }.getOrNull()
            }
            if (text == null) {
                toast("ファイルを読み込めませんでした")
                return@launch
            }
            when (val decoded = WeightFile.decode(text, ::newId, System.currentTimeMillis())) {
                is WeightFile.Result.Ok -> {
                    repository.addAll(decoded.decoded.entries)
                    decoded.decoded.profile?.let { p -> repository.updateProfile { p } }
                    toast("${decoded.decoded.entries.size}件を読み込みました")
                }
                is WeightFile.Result.Error -> toast(decoded.message)
            }
        }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val stats = computeStats(entries, profile, today)
    val open = entries.firstOrNull { it.id == openId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ダイエット体重ノート") },
                actions = {
                    IconButton(onClick = { editingProfile = true }) { Icon(Icons.Filled.Person, contentDescription = "身長・目標") }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "その他") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("直近の記録をテキストで共有") },
                                enabled = entries.isNotEmpty(),
                                onClick = { menu = false; runCatching { shareSummary(context, stats, profile) } },
                            )
                            DropdownMenuItem(
                                text = { Text("一覧をテキストで共有") },
                                enabled = entries.isNotEmpty(),
                                onClick = { menu = false; runCatching { shareEntries(context, entries) } },
                            )
                            DropdownMenuItem(
                                text = { Text("バックアップを共有(ファイル)") },
                                enabled = entries.isNotEmpty(),
                                onClick = { menu = false; runCatching { shareBackup(context, entries, profile) } },
                            )
                            DropdownMenuItem(text = { Text("バックアップを読み込む") }, onClick = { menu = false; importer.launch(arrayOf("*/*")) })
                            DropdownMenuItem(
                                text = { Text("サンプルを追加") },
                                onClick = { menu = false; scope.launch { repository.addAll(sampleEntries(today, ::newId, System.currentTimeMillis())) } },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (entries.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("体重を記録") },
                )
            }
        },
    ) { padding ->
        ListScreen(
            entries = entries, stats = stats, profile = profile, contentPadding = padding,
            onOpen = { openId = it.id }, onAdd = { adding = true },
            onAddSample = { scope.launch { repository.addAll(sampleEntries(today, ::newId, System.currentTimeMillis())) } },
            onImport = { importer.launch(arrayOf("*/*")) },
        )
    }

    if (adding) {
        WeightEditDialog(
            entry = null, today = today, existingDates = entries.map { it.date }.toSet(),
            onSave = { e -> scope.launch { repository.upsert(e) }; adding = false },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    open?.let { current ->
        WeightEditDialog(
            entry = current, today = today, existingDates = entries.map { it.date }.toSet(),
            onSave = { e -> scope.launch { repository.upsert(e) }; openId = null },
            onDelete = { scope.launch { repository.delete(current.id) }; openId = null },
            onDismiss = { openId = null },
        )
    }
    if (editingProfile) {
        ProfileEditDialog(
            profile = profile,
            onSave = { p -> scope.launch { repository.updateProfile { p } }; editingProfile = false },
            onDismiss = { editingProfile = false },
        )
    }
}

/** 上限までだけ読む(巨大なファイルを選ばれても、メモリを使い切らないように) */
private fun readLimited(input: java.io.InputStream, max: Int): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < max) {
        val n = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}
