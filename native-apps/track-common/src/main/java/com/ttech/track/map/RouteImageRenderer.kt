package com.ttech.track.map

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ttech.track.domain.GeoBounds
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.MapViewport
import com.ttech.track.domain.RouteSegments
import com.ttech.track.domain.TileUrls
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * ルートを地図の上に描いた画像を作る(一覧のサムネイル・共有用)。
 * 地図タイルの取得に失敗しても(通信できないとき)、背景色の上にルートだけは描いて画像を返す。
 * [tilesLoaded] が false のときは、地図が入っていない画像なので、あとで作り直せるように呼び出し側が覚えておく。
 */
class RouteImageRenderer(private val tiles: TileRepository) {
    class Result(val bitmap: Bitmap, val tilesLoaded: Boolean)

    /**
     * @param bottomInsetPx 画像の下に、文字などを重ねるために空けておく高さ。ルートはその上の範囲にちょうど収める
     * @param overlay 地図とルートを描いた後に、上から重ねて描く処理(統計の文字など)
     */
    suspend fun render(
        route: RouteSegments,
        mapStyle: MapStyle,
        routeStyle: RouteStyle,
        width: Int,
        height: Int,
        paddingPx: Int,
        density: Float,
        bottomInsetPx: Int = 0,
        overlay: ((Canvas, MapViewport) -> Unit)? = null,
    ): Result? {
        val points = route.all
        val bounds = GeoBounds.of(points) ?: return null

        // ルートが収まるズームは、下に空ける部分を除いた高さで決める
        val inner = MapViewport.fit(bounds, width, height - bottomInsetPx, paddingPx, scale = density.toDouble())
        val shifted = inner.latAt(inner.heightPx / 2.0 + bottomInsetPx / 2.0)
        val vp = MapViewport(shifted, inner.centerLon, inner.zoom, width, height, density.toDouble())

        val range = vp.visibleTiles()
        val wanted = ArrayList<Triple<Int, Int, Int>>()
        for (y in range.minY..range.maxY) for (x in range.minX..range.maxX) wanted.add(Triple(range.z, x, y))
        // 想定外に大きい範囲(タイルが多すぎる)は、通信を抑えるため取得せず背景のまま描く
        val fetch = if (wanted.size <= MAX_TILES) wanted else emptyList()
        val loaded = coroutineScope {
            fetch.map { (z, x, y) -> async { tiles.bitmap(mapStyle, z, x, y) } }.awaitAll()
        }
        val tilesLoaded = fetch.isNotEmpty() && loaded.all { it != null }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        MapPainter.drawBackground(canvas, mapStyle)
        MapPainter.drawTiles(canvas, vp, mapStyle) { z, x, y -> tiles.cached(mapStyle, z, x, y) }
        MapPainter.drawRoute(canvas, vp, route, routeStyle, density)
        MapPainter.drawMarkers(canvas, vp, points.firstOrNull(), points.lastOrNull(), density)
        overlay?.invoke(canvas, vp)
        MapPainter.drawAttribution(canvas, width, height, density, mapStyle)
        return Result(bitmap, tilesLoaded)
    }

    private companion object {
        const val MAX_TILES = 80
    }
}

private val unusedAttribution = TileUrls.ATTRIBUTION
