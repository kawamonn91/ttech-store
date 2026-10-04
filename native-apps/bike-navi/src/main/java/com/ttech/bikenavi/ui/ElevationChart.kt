package com.ttech.bikenavi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ttech.bikenavi.domain.ElevationPoint
import kotlin.math.abs
import kotlin.math.roundToInt

/** 勾配の目安の色(自転車でよく使う区分け: 3%未満は緑、3〜6%は黄、6〜9%は橙、9%以上は赤) */
private fun gradeColor(percent: Double): Color {
    val g = abs(percent)
    return when {
        g < 3.0 -> Color(0xFF86EFAC)
        g < 6.0 -> Color(0xFFFDE047)
        g < 9.0 -> Color(0xFFFB923C)
        else -> Color(0xFFF87171)
    }
}

/** 標高のグラフ(勾配の区間ごとに色を変えた、塗りつぶしの折れ線グラフ) */
@Composable
fun ElevationChart(elevation: List<ElevationPoint>, modifier: Modifier = Modifier) {
    if (elevation.size < 2) return
    val minEle = elevation.minOf { it.eleM }
    val maxEle = elevation.maxOf { it.eleM }
    val totalM = elevation.last().atM.coerceAtLeast(1.0)
    val eleRange = (maxEle - minEle).coerceAtLeast(10.0)

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${maxEle.roundToInt()} m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width
                val h = size.height
                fun x(atM: Double) = (atM / totalM * w).toFloat()
                fun y(ele: Double) = (h - ((ele - minEle) / eleRange * h)).toFloat()

                for (i in 0 until elevation.size - 1) {
                    val a = elevation[i]
                    val b = elevation[i + 1]
                    val distM = (b.atM - a.atM).coerceAtLeast(0.1)
                    val gradePercent = (b.eleM - a.eleM) / distM * 100.0
                    val path = Path().apply {
                        moveTo(x(a.atM), h)
                        lineTo(x(a.atM), y(a.eleM))
                        lineTo(x(b.atM), y(b.eleM))
                        lineTo(x(b.atM), h)
                        close()
                    }
                    drawPath(path, gradeColor(gradePercent))
                }

                val outline = Path().apply {
                    moveTo(x(elevation.first().atM), y(elevation.first().eleM))
                    for (p in elevation.drop(1)) lineTo(x(p.atM), y(p.eleM))
                }
                drawPath(outline, Color(0xFF374151), style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${minEle.roundToInt()} m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(shortDistance(elevation.last().atM), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GradeLegend("〜3%", Color(0xFF86EFAC))
            GradeLegend("3〜6%", Color(0xFFFDE047))
            GradeLegend("6〜9%", Color(0xFFFB923C))
            GradeLegend("9%〜", Color(0xFFF87171))
        }
    }
}

@Composable
private fun GradeLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
