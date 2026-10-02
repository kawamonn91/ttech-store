package com.ttech.navi.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ttech.navi.NaviContainer
import com.ttech.navi.data.CurrentLocation
import com.ttech.navi.domain.Place
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.MapViewport
import com.ttech.track.map.MapPainter
import java.util.Locale
import kotlin.math.log2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 地図を動かして、中心の印(ピン)の場所を目的地にする。検索で見つからない場所を選ぶときに使う */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(container: NaviContainer, onPicked: (Place) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val perm by rememberPermState()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember { mutableStateOf<MapViewport?>(null) }
    var tileVersion by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }

    fun locateMe() {
        locating = true
        scope.launch {
            try {
                val here = CurrentLocation.get(context)
                if (here != null) {
                    val current = viewport
                    viewport = if (current != null) {
                        current.copy(centerLat = here.lat, centerLon = here.lon, zoom = current.zoom.coerceAtLeast(15.0))
                    } else {
                        MapViewport(here.lat, here.lon, 15.0, size.width, size.height, density.toDouble())
                    }
                }
            } finally {
                locating = false
            }
        }
    }

    val actions = rememberPermissionActions(onResult = { locateMe() })

    // 最初は、いまの場所(なければ日本全体)を映す。画面の大きさが変わったら、それに合わせる
    LaunchedEffect(size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val current = viewport
        viewport = if (current == null) {
            val start = CurrentLocation.lastKnown(context)
            MapViewport(start?.lat ?: 36.2, start?.lon ?: 138.0, if (start != null) 14.0 else 5.5, size.width, size.height, density.toDouble())
        } else {
            current.copy(widthPx = size.width, heightPx = size.height)
        }
    }

    val vp = viewport
    LaunchedEffect(vp?.tileZoom, vp?.centerLat, vp?.centerLon, vp?.zoom, size) {
        val current = vp ?: return@LaunchedEffect
        val range = current.visibleTiles()
        if (range.count > 120) return@LaunchedEffect
        for (y in range.minY..range.maxY) for (x in range.minX..range.maxX) {
            if (container.tiles.cached(range.z, x, y) != null) continue
            launch { if (container.tiles.bitmap(range.z, x, y) != null) tileVersion++ }
        }
    }

    // 地図を止めて少し待ったら、中心の場所の住所を調べる
    LaunchedEffect(vp?.centerLat, vp?.centerLon) {
        val current = vp ?: return@LaunchedEffect
        label = null
        if (current.zoom < 9.0) return@LaunchedEffect
        delay(700)
        label = try {
            container.gsi.reverse(current.centerLat, current.centerLon)?.let { r ->
                val region = container.muni.regionOf(r.muniCd)
                region.prefecture + (region.city ?: "") + r.town
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val current = viewport ?: return@detectTransformGestures
                        val newZoom = (current.zoom + log2(zoomChange.toDouble())).coerceIn(MapViewport.MIN_ZOOM, MapViewport.MAX_ZOOM)
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
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { offset ->
                        val current = viewport ?: return@detectTapGestures
                        viewport = current.copy(
                            zoom = (current.zoom + 1.0).coerceAtMost(MapViewport.MAX_ZOOM),
                            centerLat = current.latAt(offset.y.toDouble()),
                            centerLon = current.lonAt(offset.x.toDouble()),
                        )
                    })
                },
        ) {
            @Suppress("UNUSED_EXPRESSION") tileVersion
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                MapPainter.drawBackground(c, MapStyle.Light)
                val current = viewport ?: return@drawIntoCanvas
                MapPainter.drawTiles(c, current, MapStyle.Light) { z, x, y -> container.tiles.cached(z, x, y) }
                drawPin(c, current.widthPx / 2f, current.heightPx / 2f, density)
                MapPainter.drawAttribution(c, current.widthPx, current.heightPx, density, MapStyle.Light)
            }
        }

        TopAppBar(
            title = { Text("地図で目的地を選ぶ") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
        )

        FloatingActionButton(
            onClick = {
                if (!perm.location) actions.requestLocation() else locateMe()
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp),
        ) {
            if (locating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.MyLocation, contentDescription = "現在地に戻す")
        }

        Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label ?: "地図を動かして、ピンを目的地に合わせてください", style = MaterialTheme.typography.bodyMedium)
                Button(
                    enabled = vp != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val v = vp ?: return@Button
                        val coords = String.format(Locale.US, "緯度 %.4f 経度 %.4f", v.centerLat, v.centerLon)
                        onPicked(Place("地図で選んだ場所", label ?: coords, v.centerLat, v.centerLon))
                    },
                ) { Text("この場所を目的地にする") }
            }
        }
    }
}

/** 中心の印(赤いピン)。ピンの先が、選ぶ場所 */
private fun drawPin(c: android.graphics.Canvas, x: Float, y: Float, density: Float) {
    val r = 11f * density
    val head = y - 26f * density
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(60, 0, 0, 0) }
    c.drawCircle(x, y, 4f * density, shadow)
    val body = Path().apply {
        moveTo(x, y)
        lineTo(x - r * 0.75f, head + r * 0.7f)
        lineTo(x + r * 0.75f, head + r * 0.7f)
        close()
    }
    val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#DC2626") }
    c.drawPath(body, red)
    c.drawCircle(x, head, r, red)
    c.drawCircle(x, head, r * 0.42f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE })
}
