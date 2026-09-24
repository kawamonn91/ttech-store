package com.ttech.track.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.track.domain.Geometry
import com.ttech.track.domain.LatLon
import com.ttech.track.domain.Geometry.simplify
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 横軸が時間(または距離)、縦軸が値(速度・標高・ペース)の折れ線グラフ。
 * 点が多い記録でも軽く描けるよう、形を保ったまま間引いて描く。指でなぞると、その位置の添字を [onCursor] に返す
 * (地図の上に「いまここ」の印を出すのに使う)。
 *
 * @param invertY true なら、値が小さいほど上に描く(ペースは小さいほど速いので、速いところが山になる)
 */
@Composable
fun LineChart(
    xs: DoubleArray,
    ys: DoubleArray,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    yLabel: (Double) -> String = { it.toInt().toString() },
    xLabel: (Double) -> String = { it.toInt().toString() },
    invertY: Boolean = false,
    minSpanY: Double = 1.0,
    cursorIndex: Int? = null,
    onCursor: ((Int?) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 10.sp) }

    val indices = remember(xs, ys) { Geometry.downsampleIndices(xs, ys, 320) }
    val cursorModifier = if (onCursor == null || xs.size < 2) Modifier else Modifier
        .pointerInput(xs) {
            detectDragGestures(
                onDragStart = { pos -> onCursor(nearestIndex(xs, pos.x, size.width.toFloat(), leftPad = 44.dp.toPx())) },
                onDrag = { change, _ -> onCursor(nearestIndex(xs, change.position.x, size.width.toFloat(), leftPad = 44.dp.toPx())) },
                onDragEnd = { onCursor(null) },
                onDragCancel = { onCursor(null) },
            )
        }
        .pointerInput(xs) {
            detectTapGestures(onPress = { pos ->
                onCursor(nearestIndex(xs, pos.x, size.width.toFloat(), leftPad = 44.dp.toPx()))
                tryAwaitRelease()
                onCursor(null)
            })
        }

    Canvas(modifier.fillMaxWidth().height(height).then(cursorModifier)) {
        if (xs.size < 2 || ys.size != xs.size) return@Canvas
        val leftPad = 44.dp.toPx()
        val bottomPad = 16.dp.toPx()
        val topPad = 6.dp.toPx()
        val w = size.width - leftPad
        val h = size.height - bottomPad - topPad

        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (i in indices) {
            minY = min(minY, ys[i])
            maxY = max(maxY, ys[i])
        }
        if (maxY - minY < minSpanY) {
            val mid = (maxY + minY) / 2
            minY = mid - minSpanY / 2
            maxY = mid + minSpanY / 2
        }
        val pad = (maxY - minY) * 0.08
        minY -= pad
        maxY += pad
        val minX = xs.first()
        val maxX = xs.last()
        val spanX = max(1e-9, maxX - minX)

        fun px(x: Double) = leftPad + ((x - minX) / spanX * w).toFloat()
        fun py(y: Double): Float {
            val t = ((y - minY) / (maxY - minY)).toFloat()
            return topPad + (if (invertY) t else 1f - t) * h
        }

        // 横の目盛り線(3本)と、縦軸の数値
        for (k in 0..2) {
            val v = minY + (maxY - minY) * k / 2.0
            val y = py(v)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            val text = yLabel(v)
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(leftPad - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }
        // 横軸の両端の数値
        val start = measurer.measure(xLabel(minX), labelStyle)
        val end = measurer.measure(xLabel(maxX), labelStyle)
        drawText(start, topLeft = Offset(leftPad, size.height - start.size.height))
        drawText(end, topLeft = Offset(size.width - end.size.width, size.height - end.size.height))

        val line = Path()
        val area = Path()
        var first = true
        for (i in indices) {
            val x = px(xs[i])
            val y = py(ys[i])
            if (first) {
                line.moveTo(x, y)
                area.moveTo(x, topPad + h)
                area.lineTo(x, y)
                first = false
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(px(xs[indices.last()]), topPad + h)
        area.close()
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.02f)), startY = topPad, endY = topPad + h))
        drawPath(line, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        cursorIndex?.let { ci ->
            if (ci in xs.indices) {
                val x = px(xs[ci])
                drawLine(Color.White.copy(alpha = 0.8f), Offset(x, topPad), Offset(x, topPad + h), strokeWidth = 1.5f)
                drawCircle(Color.White, 5.dp.toPx(), Offset(x, py(ys[ci])))
                drawCircle(color, 3.dp.toPx(), Offset(x, py(ys[ci])))
            }
        }
    }
}

/** 画面のx座標(px)に一番近い点の添字。凡例の左の余白は、グラフの外なので [leftPad] を引いて数える */
private fun nearestIndex(xs: DoubleArray, screenX: Float, widthPx: Float, leftPad: Float): Int {
    val ratio = ((screenX - leftPad) / (widthPx - leftPad)).coerceIn(0f, 1f)
    val target = xs.first() + (xs.last() - xs.first()) * ratio
    var lo = 0
    var hi = xs.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (xs[mid] < target) lo = mid + 1 else hi = mid
    }
    val prev = max(0, lo - 1)
    return if (abs(xs[prev] - target) <= abs(xs[lo] - target)) prev else lo
}

/**
 * 一覧のサムネイル用の、地図なしの簡易なルート図。
 * 地図の画像がまだ無い(通信できなかった・作成中)ときの代わりにもなる。
 */
@Composable
fun RouteSketch(
    points: List<LatLon>,
    lineColor: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val simplified = remember(points) { simplify(points, toleranceM = 4.0) }
    Canvas(modifier) {
        drawRect(background, size = Size(size.width, size.height))
        if (simplified.size < 2) return@Canvas
        val minLat = simplified.minOf { it.lat }
        val maxLat = simplified.maxOf { it.lat }
        val minLon = simplified.minOf { it.lon }
        val maxLon = simplified.maxOf { it.lon }
        // 緯度・経度の1度あたりの長さが違う(経度は高緯度ほど短い)ので、縦横比を補正して形をゆがめない
        val lonScale = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = max(1e-9, (maxLon - minLon) * lonScale)
        val spanY = max(1e-9, maxLat - minLat)
        val pad = 14.dp.toPx()
        val scale = min((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY).toFloat()
        val offX = (size.width - (spanX * scale).toFloat()) / 2
        val offY = (size.height - (spanY * scale).toFloat()) / 2
        fun x(lon: Double) = offX + (((lon - minLon) * lonScale) * scale).toFloat()
        fun y(lat: Double) = size.height - offY - ((lat - minLat) * scale).toFloat()
        val path = Path()
        simplified.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.lon), y(p.lat)) else path.lineTo(x(p.lon), y(p.lat)) }
        drawPath(path, lineColor.copy(alpha = 0.25f), style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color(0xFF22C55E), 4.5.dp.toPx(), Offset(x(simplified.first().lon), y(simplified.first().lat)))
        drawCircle(Color.White, 4.5.dp.toPx(), Offset(x(simplified.last().lon), y(simplified.last().lat)))
    }
}
