package com.ttech.qrgenerator.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.common.BitMatrix
import com.ttech.common.share.shareBitmap
import com.ttech.qrgenerator.domain.QrGenerator

private data class QrColor(val label: String, val dark: Color)

private val COLORS = listOf(
    QrColor("ブラック", Color(0xFF0F172A)),
    QrColor("ブランドブルー", Color(0xFF2563EB)),
    QrColor("グリーン", Color(0xFF16A34A)),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorScreen() {
    val context = LocalContext.current
    var text by remember { mutableStateOf("https://example.com") }
    var colorIndex by remember { mutableStateOf(0) }

    val matrix = remember(text) { QrGenerator.encode(text, size = 512) }
    val bitmap = remember(matrix, colorIndex) {
        matrix?.toBitmap(darkArgb = COLORS[colorIndex].dark.toArgb())
    }

    Scaffold(topBar = { TopAppBar(title = { Text("QRコード生成") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("テキスト / URL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        if (text.isNotBlank() && matrix == null) {
                            Text(
                                "生成できませんでした。文章が長すぎる可能性があります。",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            COLORS.forEachIndexed { index, c ->
                                ColorChoiceChip(
                                    label = c.label,
                                    color = c.dark,
                                    selected = index == colorIndex,
                                    onClick = { colorIndex = index },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.75f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "QRコード",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Button(
                            onClick = { bitmap?.let { shareBitmap(context, it, "qrcode.png", "QRコードを共有") } },
                            enabled = bitmap != null,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text("画像として共有・保存") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChoiceChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun BitMatrix.toBitmap(darkArgb: Int, lightArgb: Int = android.graphics.Color.WHITE): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (get(x, y)) darkArgb else lightArgb)
        }
    }
    return bmp
}
