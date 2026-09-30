package com.ttech.navi.car

import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.ttech.navi.nav.NavView
import com.ttech.navi.ui.drawNavFrame
import com.ttech.navi.ui.neededTiles
import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.MapViewport
import com.ttech.track.domain.RouteSegments
import com.ttech.track.domain.WebMercator
import com.ttech.track.map.TileRepository
import kotlin.math.ceil
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 車の位置を、画面の上から何割の高さに置くか(電話の地図[com.ttech.navi.ui.NavMap]と同じ考え方) */
private const val CAR_Y_RATIO = 0.68f

/**
 * 車の画面に、案内中の地図を描く。ホストから渡される[SurfaceContainer]に、電話の地図と同じ
 * 描画ロジック([drawNavFrame])で直接描くので、見た目は電話の地図と揃う。
 * 縮尺・向きの変化は、電話側のようなアニメーションはせず、都度そのまま反映する(簡易版)。
 */
class CarMapRenderer(private val tiles: TileRepository, private val scope: CoroutineScope) : SurfaceCallback {
    @Volatile private var container: SurfaceContainer? = null
    private var heading = 0f
    private var lastTileBucket: Triple<Int, Int, Int>? = null
    private var fetchJob: Job? = null

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        container = surfaceContainer
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        container = null
    }

    /** 案内の状況が変わるたびに呼ぶ。Surfaceがまだ無ければ何もしない */
    fun render(view: NavView, headingUp: Boolean, dark: Boolean) {
        val sc = container ?: return
        val position = view.position ?: view.route.origin
        val speedKmh = view.speedKmh ?: 0.0
        val zoom = when {
            speedKmh < 15 -> 17.0f
            speedKmh < 40 -> 16.5f
            speedKmh < 70 -> 15.8f
            speedKmh < 100 -> 15.0f
            else -> 14.4f
        }
        val bearing = view.bearing
        if (bearing != null && speedKmh >= 3.0) heading += GeoMath.angleDiffDegrees(heading.toDouble(), bearing).toFloat()
        val rotation = if (headingUp) heading else 0f

        val w = sc.width
        val h = sc.height
        val density = sc.dpi / 160f
        val carY = h * CAR_Y_RATIO
        val (vw, vh) = if (headingUp) {
            val d = ceil(2 * hypot(w / 2.0, carY.toDouble())).toInt()
            d to d
        } else {
            w to (2 * carY).toInt()
        }
        val vp = MapViewport(position.lat, position.lon, zoom.toDouble(), vw, vh, scale = density.toDouble())
        val remaining = RouteSegments(listOf(view.route.line.slice((view.progressM - 30.0).coerceAtLeast(0.0), view.progressM + 25_000.0)), emptyList())
        val style = if (dark) MapStyle.Dark else MapStyle.Light

        draw(sc, w, h, vp, carY, rotation, headingUp, heading, remaining, view, style, density)

        // 中心のタイルが変わったときだけ、足りないタイルを取りに行って、届いたら描き直す
        val tz = vp.tileZoom
        val bucket = Triple(tz, WebMercator.tileX(position.lon, tz), WebMercator.tileY(position.lat, tz))
        if (bucket != lastTileBucket) {
            lastTileBucket = bucket
            fetchJob?.cancel()
            fetchJob = scope.launch {
                var fetched = false
                for ((z, x, y) in neededTiles(vp, w, h, carY, rotation)) {
                    if (tiles.cached(z, x, y) != null) continue
                    if (tiles.bitmap(z, x, y) != null) fetched = true
                }
                if (fetched) draw(sc, w, h, vp, carY, rotation, headingUp, heading, remaining, view, style, density)
            }
        }
    }

    private fun draw(
        sc: SurfaceContainer,
        w: Int,
        h: Int,
        vp: MapViewport,
        carY: Float,
        rotation: Float,
        headingUp: Boolean,
        carHeadingDeg: Float,
        remaining: RouteSegments,
        view: NavView,
        style: MapStyle,
        density: Float,
    ) {
        val surface = sc.surface ?: return
        if (!surface.isValid) return
        val canvas = try { surface.lockCanvas(null) } catch (_: Exception) { return }
        try {
            drawNavFrame(canvas, w, h, vp, carY, rotation, headingUp, carHeadingDeg, style, remaining, view.route.destination, density, tiles::cached)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }
}
