package com.ttech.runtracker.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.domain.RunSummary
import com.ttech.track.domain.LatLon
import com.ttech.track.domain.RouteSegments
import com.ttech.track.ui.RouteSketch

val AccentOrange = Color(0xFFFC4C02)
val OkGreen = Color(0xFF16A34A)
val WarnAmber = Color(0xFFD97706)
val MapDark = Color(0xFF14110F)

/** ステータスバーの背後を不透明にして、スクロールした内容が時計・電池の表示と重ならないようにする */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** 数字を大きく、ラベルを小さく並べた、1項目の表示 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, valueSize: TextUnit = 22.sp) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 数字だけの値と、日本語まじりの値(「38分45秒」など)で、行の高さがずれないように、行の高さを決めておく
        Text(value, fontSize = valueSize, lineHeight = valueSize * 1.3f, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * ランの地図画像(保存済みのもの)。まだ無いときは、地図なしの簡易なルート図を出す。
 * [version] が変わると、画像を読み直す(あとから地図の画像ができたとき)。
 */
@Composable
fun RunThumb(container: RunContainer, run: RunSummary, version: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, run.id, version) {
        value = container.images.thumbnail(run.id)
    }
    val sketch by produceState<List<LatLon>>(initialValue = emptyList(), run.id, bitmap == null) {
        value = if (bitmap == null) RouteSegments.from(container.repository.track(run.id)).all else emptyList()
    }
    // 保存した画像の上の部分(下の文字のカードを除いた範囲)だけを見せる。幅1080:高さ830
    Box(modifier.fillMaxWidth().aspectRatio(1080f / 830f).clip(RoundedCornerShape(12.dp)).background(MapDark)) {
        val b = bitmap
        if (b != null) {
            // 保存した画像は縦長(4:5)。カードでは横長に切り取り、ルートのある中央を見せる
            Image(b.asImageBitmap(), contentDescription = "ルートの地図", contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else {
            RouteSketch(sketch, lineColor = AccentOrange, background = MapDark, modifier = Modifier.fillMaxSize())
        }
    }
}
