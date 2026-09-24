package com.ttech.track.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.ttech.track.domain.GeoBounds
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.MapViewport
import com.ttech.track.domain.RouteSegments
import com.ttech.track.map.MapPainter
import com.ttech.track.map.RouteStyle
import com.ttech.track.map.TileRepository
import kotlinx.coroutines.launch

/**
 * 指で動かせる地図(ドラッグで移動、2本指で拡大縮小、ダブルタップで拡大)。
 * タイルは、見える範囲のぶんだけ取得して描く。取得できたものから順に表示される。
 *
 * [marker] に緯度経度を渡すと、その位置に印(グラフをなぞったときの現在位置など)を出す。
 */
@Composable
fun RouteMapView(
    route: RouteSegments,
    tiles: TileRepository,
    mapStyle: MapStyle,
    routeStyle: RouteStyle,
    modifier: Modifier = Modifier,
    marker: com.ttech.track.domain.LatLon? = null,
    markerColor: Int = android.graphics.Color.WHITE,
) {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val points = remember(route) { route.all }
    val bounds = remember(route) { GeoBounds.of(points) }
    var viewport by remember { mutableStateOf<MapViewport?>(null) }
    var tileVersion by remember { mutableIntStateOf(0) }

    // 画面の大きさが決まったら、ルート全体が収まる表示にする
    LaunchedEffect(size, bounds) {
        if (size.width > 0 && size.height > 0 && bounds != null) {
            viewport = MapViewport.fit(bounds, size.width, size.height, paddingPx = (48 * density).toInt(), scale = density.toDouble())
        }
    }

    val vp = viewport
    // 表示範囲のタイルを取得する。見える範囲・ズームが変わるたびにやり直す
    LaunchedEffect(vp?.tileZoom, vp?.centerLat, vp?.centerLon, vp?.zoom, size, mapStyle) {
        val current = vp ?: return@LaunchedEffect
        val range = current.visibleTiles()
        if (range.count > 100) return@LaunchedEffect
        for (y in range.minY..range.maxY) for (x in range.minX..range.maxX) {
            if (tiles.cached(range.z, x, y) != null) continue
            launch {
                if (tiles.bitmap(range.z, x, y) != null) tileVersion++
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds() // 背景色を塗る処理が、この地図の枠の外まで塗らないようにする
            .onSizeChanged { size = it }
            .pointerInput(bounds) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    val current = viewport ?: return@detectTransformGestures
                    val newZoom = (current.zoom + kotlin.math.log2(zoomChange.toDouble())).coerceIn(MapViewport.MIN_ZOOM, MapViewport.MAX_ZOOM)
                    // 指の下の地点が動かないように、拡大縮小の中心を保ったまま、パンの分だけずらす
                    val anchorLat = current.latAt(centroid.y.toDouble())
                    val anchorLon = current.lonAt(centroid.x.toDouble())
                    val scaled = current.copy(zoom = newZoom)
                    val dx = centroid.x - scaled.toScreenX(anchorLon) + pan.x
                    val dy = centroid.y - scaled.toScreenY(anchorLat) + pan.y
                    viewport = scaled.copy(
                        centerLat = scaled.latAt(scaled.heightPx / 2.0 - dy),
                        centerLon = scaled.lonAt(scaled.widthPx / 2.0 - dx),
                    )
                }
            }
            .pointerInput(bounds) {
                detectTapGestures(onDoubleTap = { offset ->
                    val current = viewport ?: return@detectTapGestures
                    val newZoom = (current.zoom + 1.0).coerceAtMost(MapViewport.MAX_ZOOM)
                    val lat = current.latAt(offset.y.toDouble())
                    val lon = current.lonAt(offset.x.toDouble())
                    viewport = current.copy(zoom = newZoom, centerLat = lat, centerLon = lon)
                })
            },
    ) {
        @Suppress("UNUSED_EXPRESSION") tileVersion // タイルが届くたびに描き直す
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            MapPainter.drawBackground(c, mapStyle)
            val current = viewport ?: return@drawIntoCanvas
            MapPainter.drawTiles(c, current, mapStyle) { z, x, y -> tiles.cached(z, x, y) }
            MapPainter.drawRoute(c, current, route, routeStyle, density)
            MapPainter.drawMarkers(c, current, points.firstOrNull(), points.lastOrNull(), density)
            marker?.let { m ->
                val x = current.toScreenX(m.lon).toFloat()
                val y = current.toScreenY(m.lat).toFloat()
                val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = markerColor }
                c.drawCircle(x, y, 9f * density, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(100, 0, 0, 0) })
                c.drawCircle(x, y, 7f * density, ring)
                c.drawCircle(x, y, 3.5f * density, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = routeStyle.coreColor })
            }
            MapPainter.drawAttribution(c, current.widthPx, current.heightPx, density, mapStyle)
        }
    }
}

