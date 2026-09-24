package com.ttech.voicememo.ui

import android.Manifest
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.ttech.common.share.shareTextFile
import com.ttech.voicememo.data.MemoStore
import com.ttech.voicememo.domain.Memo
import com.ttech.voicememo.domain.Transcript
import com.ttech.voicememo.domain.exportText
import com.ttech.voicememo.domain.isValidMemoText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMemoScreen() {
    val context = LocalContext.current
    val store = remember { MemoStore(context) }
    val scope = rememberCoroutineScope()
    val memos by store.memos.collectAsState(initial = emptyList())

    val supported = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf(Transcript()) }

    val session = remember {
        SpeechSession(context) { event ->
            when (event) {
                is SpeechEvent.Partial -> transcript = transcript.withPartial(event.text)
                is SpeechEvent.Final -> transcript = transcript.withFinal(event.text)
                is SpeechEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                SpeechEvent.Ended -> {
                    transcript = transcript.flushed()
                    listening = false
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { session.destroy() } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
            session.start()
        } else {
            Toast.makeText(context, "マイクの許可が必要です", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleListening() {
        if (listening) session.stop() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun saveMemo() {
        val text = transcript.text.trim()
        if (!isValidMemoText(text)) return
        val memo = Memo(id = UUID.randomUUID().toString(), date = LocalDate.now().toString(), text = text)
        scope.launch { store.add(memo) }
        transcript = Transcript()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("音声メモ文字起こし") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!supported) {
                item {
                    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(
                            "この端末では音声認識を利用できません。Googleアプリを最新の状態にするか、音声認識サービスを有効にしてください。",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::toggleListening, modifier = Modifier.fillMaxWidth()) {
                                Text(if (listening) "録音を停止" else "録音を開始")
                            }
                            if (listening) {
                                Text("聞き取り中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (transcript.text.isNotEmpty()) {
                                Text(transcript.text, style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(
                                    onClick = ::saveMemo,
                                    enabled = !listening,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("メモとして保存") }
                            }
                        }
                    }
                }
            }

            if (memos.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = { shareTextFile(context, exportText(memos), "voice-memos.txt", chooserTitle = "メモを書き出す") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("全メモを.txtで書き出す") }
                }
            }

            item {
                Text(
                    "保存済みメモ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (memos.isEmpty()) {
                item { Text("まだメモがありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(memos, key = Memo::id) { memo ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(memo.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { scope.launch { store.remove(memo.id) } }) { Text("削除") }
                        }
                        Text(memo.text)
                    }
                }
            }

            item {
                Text(
                    "※ 音声認識にはお使いの端末の標準機能(Google)を使用します。認識処理の一部でGoogleのサーバーが利用されることがあります。" +
                        "文字起こし結果自体はこの端末にのみ保存されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
