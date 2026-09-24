package com.ttech.driverecord.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.driverecord.DriveContainer
import com.ttech.driverecord.domain.DriveSummary
import com.ttech.track.domain.RouteSegments
import com.ttech.track.ui.RouteSketch

val AccentRed = Color(0xFFE11D48)
val OkGreen = Color(0xFF16A34A)
val WarnAmber = Color(0xFFD97706)
val MapDark = Color(0xFF0F1216)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** 数字を大きく、ラベルを小さく並べた、1項目の表示 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, valueSize: androidx.compose.ui.unit.TextUnit = 22.sp) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = valueSize, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
                colors = if (danger) ButtonDefaults.buttonColors(containerColor = AccentRed) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

/**
 * 記録の地図画像(保存済みのもの)。まだ無いときは、地図なしの簡易なルート図を出す。
 * [version] が変わると、画像を読み直す(あとから地図の画像ができたとき)。
 */
@Composable
fun DriveThumb(
    container: DriveContainer,
    drive: DriveSummary,
    version: Int,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, drive.id, version) {
        value = container.images.thumbnail(drive.id)
    }
    val sketch by produceState<List<com.ttech.track.domain.LatLon>>(initialValue = emptyList(), drive.id, bitmap == null) {
        value = if (bitmap == null) RouteSegments.from(container.repository.track(drive.id)).all else emptyList()
    }
    // 保存した画像の上の部分(下の文字のカードを除いた範囲)だけを見せる。幅1080:高さ890
    Box(modifier.fillMaxWidth().aspectRatio(1080f / 890f).clip(RoundedCornerShape(16.dp)).background(MapDark)) {
        val b = bitmap
        if (b != null) {
            // 保存した画像は縦長(4:5)。カードでは横長に切り取り、ルートのある中央を見せる
            Image(b.asImageBitmap(), contentDescription = "ルートの地図", contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else {
            RouteSketch(sketch, lineColor = Color(0xFFFF453A), background = MapDark, modifier = Modifier.fillMaxSize())
        }
    }
}


@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
