package com.ttech.sheetmusicviewer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ttech.sheetmusicviewer.data.loadPageBitmap
import com.ttech.sheetmusicviewer.domain.PageTurn
import com.ttech.sheetmusicviewer.domain.nextPageIndex
import com.ttech.sheetmusicviewer.domain.pageLabel
import com.ttech.sheetmusicviewer.domain.prevPageIndex
import com.ttech.sheetmusicviewer.domain.turnForTap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_DIMENSION = 2400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetMusicViewerScreen() {
    val context = LocalContext.current
    var pages by remember { mutableStateOf(listOf<Uri>()) }
    var index by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            pages = uris
            index = 0
        }
    }
    fun pickImages() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun next() { index = nextPageIndex(index, pages.size) }
    fun prev() { index = prevPageIndex(index) }

    // 演奏中に画面が消えないようにする。
    val view = LocalView.current
    val viewing = pages.isNotEmpty()
    DisposableEffect(viewing) {
        view.keepScreenOn = viewing
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(viewing) { if (viewing) focusRequester.requestFocus() }

    Scaffold(topBar = { TopAppBar(title = { Text("楽譜めくりアプリ") }) }) { padding ->
        if (!viewing) {
            Column(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "楽譜の画像を複数選択してください(ページ順)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = ::pickImages, modifier = Modifier.fillMaxWidth()) { Text("楽譜を選ぶ") }
                    }
                }
                Text(
                    "※ 画面の左右をタップしてページをめくれます。矢印キー(←→)・スペースキー・PageUp/PageDownにも対応しているので、" +
                        "キーボードとして動作するBluetoothフットペダル(AirTurn等)もそのままお使いいただけます。" +
                        "表示中は画面が消えません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            val bitmap by produceState<ImageBitmap?>(initialValue = null, pages, index) {
                value = null
                val uri = pages[index]
                value = withContext(Dispatchers.IO) { loadPageBitmap(context, uri, MAX_DIMENSION)?.asImageBitmap() }
            }

            Column(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionRight, Key.Spacebar, Key.PageDown -> { next(); true }
                            Key.DirectionLeft, Key.PageUp -> { prev(); true }
                            else -> false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pageLabel(index, pages.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = ::pickImages) { Text("別の楽譜を読み込む") }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(pages.size) {
                            detectTapGestures { offset ->
                                when (turnForTap(offset.x, size.width.toFloat())) {
                                    PageTurn.PREV -> prev()
                                    PageTurn.NEXT -> next()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "ページ ${index + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::prev, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("前へ") }
                    Button(onClick = ::next, enabled = index < pages.size - 1, modifier = Modifier.weight(1f)) { Text("次へ") }
                }
            }
        }
    }
}
