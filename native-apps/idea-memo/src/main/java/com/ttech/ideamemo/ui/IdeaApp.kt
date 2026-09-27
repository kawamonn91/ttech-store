package com.ttech.ideamemo.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import com.ttech.ideamemo.data.IdeaRepository
import com.ttech.ideamemo.domain.IdeaFile
import com.ttech.ideamemo.domain.sampleIdeas
import com.ttech.ideamemo.share.shareBackup
import com.ttech.ideamemo.share.shareIdeas
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaApp(repository: IdeaRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by repository.loaded.collectAsState()
    val ideas by repository.ideas.collectAsState()

    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<com.ttech.ideamemo.domain.Idea?>(null) }
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun newId() = UUID.randomUUID().toString()

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { readLimited(it, IdeaFile.MAX_BYTES + 1) } }.getOrNull()
            }
            if (text == null) {
                toast("ファイルを読み込めませんでした")
                return@launch
            }
            when (val decoded = IdeaFile.decode(text, ::newId, System.currentTimeMillis())) {
                is IdeaFile.Decoded.Ok -> {
                    repository.addAll(decoded.ideas)
                    toast("${decoded.ideas.size}件を読み込みました")
                }
                is IdeaFile.Decoded.Error -> toast(decoded.message)
            }
        }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val open = ideas.firstOrNull { it.id == openId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("アプリのアイデア帳") },
                actions = {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "その他") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("リストをテキストで共有") },
                                enabled = ideas.isNotEmpty(),
                                onClick = { menu = false; runCatching { shareIdeas(context, ideas) } },
                            )
                            DropdownMenuItem(
                                text = { Text("バックアップを共有(ファイル)") },
                                enabled = ideas.isNotEmpty(),
                                onClick = { menu = false; runCatching { shareBackup(context, ideas) } },
                            )
                            DropdownMenuItem(text = { Text("メモファイルを読み込む") }, onClick = { menu = false; importer.launch(arrayOf("*/*")) })
                            DropdownMenuItem(
                                text = { Text("サンプルを追加") },
                                onClick = { menu = false; scope.launch { repository.addAll(sampleIdeas(::newId, System.currentTimeMillis())) } },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (ideas.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("アイデアを追加") },
                )
            }
        },
    ) { padding ->
        ListScreen(
            ideas = ideas,
            contentPadding = padding,
            onOpen = { openId = it.id },
            onAdd = { adding = true },
            onAddSample = { scope.launch { repository.addAll(sampleIdeas(::newId, System.currentTimeMillis())) } },
            onImport = { importer.launch(arrayOf("*/*")) },
        )
    }

    if (adding) {
        IdeaEditDialog(
            idea = null,
            onSave = { idea -> scope.launch { repository.upsert(idea) }; adding = false },
            onDismiss = { adding = false },
        )
    }
    editing?.let { current ->
        IdeaEditDialog(
            idea = current,
            onSave = { idea -> scope.launch { repository.upsert(idea) }; editing = null },
            onDismiss = { editing = null },
        )
    }
    if (open != null) {
        IdeaDetailSheet(
            idea = open,
            onDismiss = { openId = null },
            onEdit = { editing = open },
            onDelete = { scope.launch { repository.delete(open.id); openId = null } },
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
