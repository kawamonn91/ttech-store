package com.ttech.imagebatch.ui

import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.common.share.shareBitmap
import com.ttech.imagebatch.data.processImage
import com.ttech.imagebatch.domain.COLOR_PRESETS
import com.ttech.imagebatch.domain.SIZE_PRESETS
import com.ttech.imagebatch.domain.SizePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class ProcessedImage(val id: String, val name: String, val bitmap: Bitmap)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageBatchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bgColor by remember { mutableStateOf(COLOR_PRESETS[0]) }
    var preset by remember { mutableStateOf(SIZE_PRESETS[0]) }
    var results by remember { mutableStateOf(listOf<ProcessedImage>()) }
    var busy by remember { mutableStateOf(false) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val processed = withContext(Dispatchers.Default) {
                uris.map { uri ->
                    val bitmap = processImage(context, uri, bgColor, preset.width, preset.height)
                    ProcessedImage(id = UUID.randomUUID().toString(), name = displayNameOf(context, uri), bitmap = bitmap)
                }
            }
            results = processed
            busy = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("画像背景合成・リサイズ") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("背景色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            COLOR_PRESETS.forEach { c ->
                                ColorSwatch(color = Color(c), selected = bgColor == c, onClick = { bgColor = c })
                            }
                        }

                        Text(
                            "出力サイズ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SIZE_PRESETS.forEach { p ->
                                SizeChip(preset = p, selected = preset == p, onClick = { preset = p })
                            }
                        }

                        Button(
                            onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) { Text("画像を選択(複数可)") }

                        if (busy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("処理中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                item {
                    val rows = (results.size + 1) / 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height(260.dp * rows),
                        userScrollEnabled = false,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(results, key = { it.id }) { img ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.foundation.Image(
                                        bitmap = img.bitmap.asImageBitmap(),
                                        contentDescription = img.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            shareBitmap(context, img.bitmap, "processed-${img.name}.png", "画像を共有・保存")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("保存") }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "※ 被写体の自動切り抜き(背景除去)は行わず、指定サイズの背景色キャンバスの中央に画像全体を配置します。" +
                        "透過PNG素材と組み合わせると背景合成に使えます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SizeChip(preset: SizePreset, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        preset.label,
        color = textColor,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun displayNameOf(context: android.content.Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) it.getString(index)?.substringBeforeLast(".") else null
        } else {
            null
        }
    } ?: "image"
}
