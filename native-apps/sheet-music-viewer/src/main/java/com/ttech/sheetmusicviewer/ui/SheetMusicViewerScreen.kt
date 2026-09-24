package com.ttech.sheetmusicviewer.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ttech.sheetmusicviewer.data.ScoreStore
import com.ttech.sheetmusicviewer.data.loadPageBitmap
import com.ttech.sheetmusicviewer.domain.PageTurn
import com.ttech.sheetmusicviewer.domain.Score
import com.ttech.sheetmusicviewer.domain.isFirst
import com.ttech.sheetmusicviewer.domain.isLast
import com.ttech.sheetmusicviewer.domain.pageLabel
import com.ttech.sheetmusicviewer.domain.pageTurnFor
import com.ttech.sheetmusicviewer.domain.turn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SheetMusicViewerScreen() {
    val context = LocalContext.current
    val store = remember { ScoreStore(context) }
    val scope = rememberCoroutineScope()
    val score by store.score.collectAsState(initial = null)

    val pickPages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) scope.launch { store.load(uris) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("楽譜めくり") }) }) { padding ->
        val current = score ?: return@Scaffold
        if (current.pages.isEmpty()) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "楽譜の画像を複数選択してください(ページ順)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { pickPages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) { Text("画像を選ぶ") }
                    }
                }
                Text(
                    "※ 矢印キー(←→)・スペースキー・PageUp/PageDownでページをめくれます。多くのBluetoothフットペダル(AirTurn等)はこれらのキーを送信するタイプのため、そのままお使いいただけます。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Viewer(
                score = current,
                modifier = Modifier.padding(padding),
                onTurn = { direction -> scope.launch { store.save(current.turn(direction)) } },
                onReload = { scope.launch { store.clear() } },
            )
        }
    }
}

@Composable
private fun Viewer(score: Score, modifier: Modifier, onTurn: (PageTurn) -> Unit, onReload: () -> Unit) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // 演奏中に画面が消えないようにする
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val uri = score.pages[score.index]
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { loadPageBitmap(context, Uri.parse(uri)) }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = pageTurnFor(event.key.nativeKeyCode) ?: return@onPreviewKeyEvent false
                onTurn(direction)
                true
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(score.pageLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onReload) { Text("別の楽譜を読み込む") }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val page = bitmap
            if (page != null) {
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = "ページ ${score.index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator()
                Text(
                    "画像を読み込んでいます(表示されない場合は、別の楽譜として読み込み直してください)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onTurn(PageTurn.PREV) }, enabled = !score.isFirst, modifier = Modifier.weight(1f)) { Text("前へ") }
            Button(onClick = { onTurn(PageTurn.NEXT) }, enabled = !score.isLast, modifier = Modifier.weight(1f)) { Text("次へ") }
        }
    }
}
