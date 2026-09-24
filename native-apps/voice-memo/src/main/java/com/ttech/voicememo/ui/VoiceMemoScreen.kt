package com.ttech.voicememo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ttech.common.share.shareTextFile
import com.ttech.voicememo.data.MemoStore
import com.ttech.voicememo.domain.Memo
import com.ttech.voicememo.domain.buildMemo
import com.ttech.voicememo.domain.exportText
import com.ttech.voicememo.domain.liveTranscript
import com.ttech.voicememo.speech.ContinuousRecognizer
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VoiceMemoScreen() {
    val context = LocalContext.current
    val store = remember { MemoStore(context) }
    val scope = rememberCoroutineScope()
    val memos by store.memos.collectAsState(initial = emptyList())

    val supported = remember { ContinuousRecognizer.isAvailable(context) }
    var listening by remember { mutableStateOf(false) }
    var liveText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val recognizer = remember {
        if (!supported) {
            null
        } else {
            ContinuousRecognizer(
                context,
                onText = { committed, partial -> liveText = liveTranscript(committed, partial) },
                onStopped = { message ->
                    listening = false
                    error = message
                },
            )
        }
    }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    fun startListening() {
        error = null
        listening = true
        recognizer?.start(liveText)
    }

    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else error = "マイクの使用が許可されていません"
    }

    fun toggleListening() {
        if (listening) {
            recognizer?.stop()
            listening = false
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun saveMemo() {
        val memo = buildMemo(UUID.randomUUID().toString(), LocalDate.now().toString(), liveText) ?: return
        scope.launch { store.add(memo) }
        liveText = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("音声メモ文字起こし") }) }) { padding ->
        if (!supported) {
            Card(Modifier.padding(padding).padding(16.dp).fillMaxWidth()) {
                Text(
                    "この端末では音声認識を利用できません。Googleアプリなどの音声入力サービスが有効か確認してください。",
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::toggleListening, modifier = Modifier.fillMaxWidth()) {
                            Text(if (listening) "録音を停止" else "録音を開始")
                        }
                        if (listening) {
                            Text(
                                "聞き取り中...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                        if (liveText.isNotEmpty() || listening) {
                            // 停止中は認識結果を手で直せる
                            OutlinedTextField(
                                value = liveText,
                                onValueChange = { liveText = it },
                                readOnly = listening,
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (liveText.isNotBlank() && !listening) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = ::saveMemo, modifier = Modifier.weight(1f)) { Text("メモとして保存") }
                                TextButton(onClick = { liveText = "" }) { Text("破棄") }
                            }
                        }
                    }
                }
            }

            if (memos.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = {
                            shareTextFile(
                                context = context,
                                content = exportText(memos),
                                filename = "voice-memos.txt",
                                chooserTitle = "メモを保存・共有",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("全メモを.txtで書き出す") }
                }
            }

            item {
                Text("保存済みメモ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (memos.isEmpty()) {
                item { Text("まだメモがありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(memos, key = Memo::id) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { store.remove(m.id) } }) { Text("削除") }
                        }
                        Text(m.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }

            item {
                Text(
                    "※ 音声認識には端末標準の音声認識サービスを使用します(多くの端末ではGoogleの音声認識で、認識処理の一部でGoogleのサーバーが利用されることがあります)。文字起こし結果自体はこの端末にのみ保存されます。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
