package com.ttech.bikenavi.ui

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.ttech.bikenavi.nav.NavView
import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.LatLon
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.MapViewport
import com.ttech.track.domain.RouteSegments
import com.ttech.track.domain.WebMercator
import com.ttech.track.map.MapPainter
import com.ttech.track.map.RouteStyle
import com.ttech.track.map.TileRepository
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

internal val BikeRouteStyle = RouteStyle(
    coreColor = AndroidColor.parseColor("#16A34A"),
    glowColor = AndroidColor.parseColor("#4ADE80"),
    coreWidthDp = 5f,
    glow = false,
)

/** 自分の位置を、画面の上から何割の高さに置くか(前方をたくさん見せるため、下寄せ) */
private const val CAR_Y_RATIO = 0.68f

/** 案内中の地図。いまの位置を中心に、速さに合わせた縮尺で映し、進行方向が上になるように回す([headingUp]) */
@Composable
fun NavMap(view: NavView, tiles: TileRepository, headingUp: Boolean, dark: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tileVersion by remember { mutableIntStateOf(0) }
    val style = if (dark) MapStyle.Dark else MapStyle.Light

    val position = view.position ?: view.route.origin
    val speedKmh = view.speedKmh ?: 0.0
    // 自転車なので、車より縮尺の変化は小さめにする
    val targetZoom = when {
        speedKmh < 10 -> 17.3f
        speedKmh < 20 -> 16.8f
        speedKmh < 30 -> 16.2f
        else -> 15.6f
    }
    val zoom by animateFloatAsState(targetZoom, tween(1500), label = "zoom")

    var heading by remember { mutableFloatStateOf(view.bearing?.toFloat() ?: 0f) }
    LaunchedEffect(view.bearing, speedKmh) {
        val b = view.bearing
        if (b != null && speedKmh >= 2.0) heading += GeoMath.angleDiffDegrees(heading.toDouble(), b).toFloat()
    }
    val animatedHeading by animateFloatAsState(heading, tween(600), label = "heading")
    val rotation = if (headingUp) animatedHeading else 0f

    val w = size.width
    val h = size.height
    val carY = h * CAR_Y_RATIO
    val viewport: MapViewport? = if (w > 0 && h > 0) {
        val (vw, vh) = if (headingUp) {
            val d = ceil(2 * hypot(w / 2.0, carY.toDouble())).toInt()
            d to d
        } else {
            w to (2 * carY).toInt()
        }
        MapViewport(position.lat, position.lon, zoom.toDouble(), vw, vh, scale = density.toDouble())
    } else {
        null
    }

    val tz = viewport?.tileZoom
    val cx = tz?.let { WebMercator.tileX(position.lon, it) }
    val cy = tz?.let { WebMercator.tileY(position.lat, it) }
    LaunchedEffect(tz, cx, cy, (rotation / 20f).roundToInt(), w, h) {
        val vp = viewport ?: return@LaunchedEffect
        for ((z, x, y) in neededTiles(vp, w, h, carY, rotation)) {
            if (tiles.cached(z, x, y) != null) continue
            launch { if (tiles.bitmap(z, x, y) != null) tileVersion++ }
        }
    }

    val remaining = remember(view.route, (view.progressM / 25).toInt()) {
        RouteSegments(listOf(view.route.line.slice((view.progressM - 30.0).coerceAtLeast(0.0), view.progressM + 15_000.0)), emptyList())
    }

    Canvas(modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }) {
        @Suppress("UNUSED_EXPRESSION") tileVersion
        drawIntoCanvas { canvas ->
            val vp = viewport ?: return@drawIntoCanvas
            drawNavFrame(canvas.nativeCanvas, w, h, vp, carY, rotation, headingUp, animatedHeading, style, remaining, view.route.destination, density, tiles::cached)
        }
    }
}

/** 地図の1コマを描く。電話の画面(Compose)でも、同じ関数で描く */
internal fun drawNavFrame(
    c: NativeCanvas,
    w: Int,
    h: Int,
    vp: MapViewport,
    carY: Float,
    rotation: Float,
    headingUp: Boolean,
    carHeadingDeg: Float,
    style: MapStyle,
    remaining: RouteSegments,
    destination: LatLon,
    density: Float,
    tile: (z: Int, x: Int, y: Int) -> Bitmap?,
) {
    MapPainter.drawBackground(c, style)
    c.save()
    c.translate(w / 2f, carY)
    c.rotate(-rotation)
    c.translate(-vp.widthPx / 2f, -vp.heightPx / 2f)
    MapPainter.drawTiles(c, vp, style, tile)
    MapPainter.drawRoute(c, vp, remaining, BikeRouteStyle, density)
    MapPainter.drawMarkers(c, vp, null, destination, density)
    c.restore()
    drawCar(c, w / 2f, carY, if (headingUp) 0f else carHeadingDeg, density)
    MapPainter.drawAttribution(c, w, h, density, style)
}

internal fun neededTiles(vp: MapViewport, w: Int, h: Int, carY: Float, rotationDeg: Float): List<Triple<Int, Int, Int>> {
    val range = vp.visibleTiles()
    val phi = Math.toRadians(-rotationDeg.toDouble())
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)
    val margin = range.tilePx * 1.75
    val n = 1 shl range.z
    val found = ArrayList<Pair<Double, Triple<Int, Int, Int>>>()
    for (y in range.minY..range.maxY) {
        if (y < 0 || y >= n) continue
        for (x in range.minX..range.maxX) {
            val lon = WebMercator.lon((x + 0.5) * WebMercator.TILE, range.z.toDouble())
            val lat = WebMercator.lat((y + 0.5) * WebMercator.TILE, range.z.toDouble())
            val dx = vp.toScreenX(lon) - vp.widthPx / 2.0
            val dy = vp.toScreenY(lat) - vp.heightPx / 2.0
            val sx = w / 2.0 + dx * cosPhi - dy * sinPhi
            val sy = carY + dx * sinPhi + dy * cosPhi
            if (sx > -margin && sx < w + margin && sy > -margin && sy < h + margin) found.add(hypot(dx, dy) to Triple(range.z, x, y))
        }
    }
    return found.sortedBy { it.first }.map { it.second }
}

/** 自分の位置の印(緑の矢印。[headingDeg] は、上を0度とした向き) */
internal fun drawCar(c: NativeCanvas, x: Float, y: Float, headingDeg: Float, density: Float) {
    c.save()
    c.translate(x, y)
    c.rotate(headingDeg)
    val r = 14f * density
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(70, 0, 0, 0) }
    c.drawCircle(0f, 2f * density, r * 1.25f, shadow)
    val arrow = Path().apply {
        moveTo(0f, -r * 1.3f)
        lineTo(r * 0.95f, r)
        lineTo(0f, r * 0.45f)
        lineTo(-r * 0.95f, r)
        close()
    }
    c.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f * density; strokeJoin = Paint.Join.ROUND })
    c.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#16A34A") })
    c.restore()
}
