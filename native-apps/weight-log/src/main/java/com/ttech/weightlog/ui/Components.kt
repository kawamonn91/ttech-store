package com.ttech.weightlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ttech.weightlog.domain.WeightEntry
import com.ttech.weightlog.domain.sortedByDateAsc

@Composable
fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { content() }
    else Card(modifier = modifier.fillMaxWidth(), colors = colors) { content() }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
    }
}

/**
 * 体重の推移の折れ線グラフ。点が1つなら点だけ、2つ以上なら線でつなぐ。[goalKg] があれば、破線で目標のラインを引く。
 * 地図・GPS系のアプリと違って値の範囲が狭いので、専用の軽いグラフをここに持つ(track-common には依存しない)。
 */
@Composable
fun WeightChart(entries: List<WeightEntry>, goalKg: Double?, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 140.dp) {
    val sorted = sortedByDateAsc(entries)
    val color = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (sorted.isEmpty()) return@Canvas
        val values = sorted.map { it.weightKg }
        val lo = minOf(values.min(), goalKg ?: values.min())
        val hi = maxOf(values.max(), goalKg ?: values.max())
        val span = (hi - lo).let { if (it < 0.5) 0.5 else it }
        val padding = 12f
        val w = size.width - padding * 2
        val h = size.height - padding * 2
        fun yOf(v: Double): Float = padding + h - ((v - lo) / span * h).toFloat()
        fun xOf(i: Int): Float = if (sorted.size <= 1) padding + w / 2 else padding + w * i / (sorted.size - 1)

        goalKg?.let { g ->
            val y = yOf(g)
            drawLine(
                color = goalColor, start = Offset(padding, y), end = Offset(size.width - padding, y),
                strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }
        val points = sorted.mapIndexed { i, e -> Offset(xOf(i), yOf(e.weightKg)) }
        for (i in 0 until points.size - 1) {
            drawLine(color = color, start = points[i], end = points[i + 1], strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        points.forEach { drawCircle(color = color, radius = 6f, center = it) }
    }
}
