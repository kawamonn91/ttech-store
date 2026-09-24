package com.ttech.track.domain

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.sin

/** 地図タイル(Web メルカトル)の座標計算。タイルは256px四方で、ズームが1増えるごとに縦横2倍 */
object WebMercator {
    const val TILE = 256.0
    const val MAX_LAT = 85.0511287798

    private fun size(zoom: Double): Double = TILE * 2.0.pow(zoom)

    /** 経度→ズーム zoom での世界座標X(px) */
    fun worldX(lon: Double, zoom: Double): Double = size(zoom) * (lon + 180.0) / 360.0

    fun worldY(lat: Double, zoom: Double): Double {
        val s = sin(Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT)))
        return size(zoom) * (0.5 - ln((1 + s) / (1 - s)) / (4 * PI))
    }

    fun lon(worldX: Double, zoom: Double): Double = worldX / size(zoom) * 360.0 - 180.0

    fun lat(worldY: Double, zoom: Double): Double {
        val n = PI - 2 * PI * worldY / size(zoom)
        return Math.toDegrees(atan(sinh(n)))
    }

    fun tileX(lon: Double, zoom: Int): Int = floor(worldX(lon, zoom.toDouble()) / TILE).toInt().coerceIn(0, (1 shl zoom) - 1)
    fun tileY(lat: Double, zoom: Int): Int = floor(worldY(lat, zoom.toDouble()) / TILE).toInt().coerceIn(0, (1 shl zoom) - 1)
}

/** 地図の見た目。タイルは同じもの(OpenStreetMap)で、ダークは描くときに色を変換して作る */
enum class MapStyle(val label: String) {
    Dark("ダーク"),
    Light("ライト"),
}

object TileUrls {
    /**
     * OpenStreetMap の標準タイル(256px)。キー不要で、出典の表示と、アプリを名乗る User-Agent が条件。
     * 大量の取得は禁止されているので、取得したタイルは端末に保存して、二度目からは通信しない。
     */
    fun url(z: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$z/$x/$y.png"

    /** 経度方向は地図が一周してつながっている。縦方向に範囲外のタイルは無い(null) */
    fun normalize(z: Int, x: Int, y: Int): Pair<Int, Int>? {
        val n = 1 shl z
        if (y < 0 || y >= n) return null
        return x.mod(n) to y
    }

    const val ATTRIBUTION = "© OpenStreetMap contributors"
}

/**
 * 画面(または画像)に映す範囲。[scale] は「世界座標の1pxが画面の何pxか」(端末の密度)。
 * ズームは小数でよい(整数のタイルを拡大縮小して描く)。
 */
data class MapViewport(
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Double,
    val widthPx: Int,
    val heightPx: Int,
    val scale: Double = 1.0,
) {
    private val cx get() = WebMercator.worldX(centerLon, zoom)
    private val cy get() = WebMercator.worldY(centerLat, zoom)

    fun toScreenX(lon: Double): Double = (WebMercator.worldX(lon, zoom) - cx) * scale + widthPx / 2.0
    fun toScreenY(lat: Double): Double = (WebMercator.worldY(lat, zoom) - cy) * scale + heightPx / 2.0

    fun lonAt(screenX: Double): Double = WebMercator.lon((screenX - widthPx / 2.0) / scale + cx, zoom)
    fun latAt(screenY: Double): Double = WebMercator.lat((screenY - heightPx / 2.0) / scale + cy, zoom)

    /**
     * タイルを描くときの整数のズーム。画面の密度が高い(scale が大きい)ときは、1つ細かいタイルを選んで、
     * タイル1枚(256px)が画面でほぼ256pxになるようにする(引き伸ばしてぼやけないように)。
     */
    val tileZoom: Int get() = Math.round(zoom + log2(scale)).toInt().coerceIn(0, MAX_TILE_ZOOM)

    /** 画面に入るタイルの範囲(x, y はタイル番号。x は一周ぶんの折り返しを考慮しない生の値) */
    fun visibleTiles(): TileRange {
        val z = tileZoom
        // タイル1枚は、ズーム z では世界座標で 256*2^(zoom-z) の大きさ。それが画面で何pxか
        val k = 2.0.pow(zoom - z) * scale
        val tilePx = WebMercator.TILE * k
        val cxT = WebMercator.worldX(centerLon, z.toDouble()) / WebMercator.TILE
        val cyT = WebMercator.worldY(centerLat, z.toDouble()) / WebMercator.TILE
        val halfW = widthPx / 2.0 / tilePx
        val halfH = heightPx / 2.0 / tilePx
        return TileRange(z, floor(cxT - halfW).toInt(), floor(cxT + halfW).toInt(), floor(cyT - halfH).toInt(), floor(cyT + halfH).toInt(), tilePx)
    }

    companion object {
        const val MAX_TILE_ZOOM = 19
        const val MIN_ZOOM = 2.0
        const val MAX_ZOOM = 19.5

        /**
         * 範囲 [bounds] が、余白 [paddingPx] を残して収まる最大のズームで、その中心に合わせた範囲を返す。
         * 1点だけ・ほぼ同じ場所のときは [maxFitZoom] で止める。
         */
        fun fit(
            bounds: GeoBounds,
            widthPx: Int,
            heightPx: Int,
            paddingPx: Int,
            scale: Double = 1.0,
            maxFitZoom: Double = 17.0,
            minZoom: Double = MIN_ZOOM,
        ): MapViewport {
            val x0 = WebMercator.worldX(bounds.minLon, 0.0)
            val x1 = WebMercator.worldX(bounds.maxLon, 0.0)
            val yTop = WebMercator.worldY(bounds.maxLat, 0.0)
            val yBottom = WebMercator.worldY(bounds.minLat, 0.0)
            val dx = x1 - x0
            val dy = yBottom - yTop
            val availW = max(1, widthPx - 2 * paddingPx).toDouble()
            val availH = max(1, heightPx - 2 * paddingPx).toDouble()
            val zx = if (dx > 1e-12) log2(availW / (scale * dx)) else Double.MAX_VALUE
            val zy = if (dy > 1e-12) log2(availH / (scale * dy)) else Double.MAX_VALUE
            val zoom = min(zx, zy).coerceIn(minZoom, maxFitZoom)
            val centerWx = (x0 + x1) / 2
            val centerWy = (yTop + yBottom) / 2
            return MapViewport(
                centerLat = WebMercator.lat(centerWy * 2.0.pow(zoom), zoom),
                centerLon = WebMercator.lon(centerWx * 2.0.pow(zoom), zoom),
                zoom = zoom,
                widthPx = widthPx,
                heightPx = heightPx,
                scale = scale,
            )
        }
    }
}

/** 画面に入るタイルの範囲。[tilePx] は、タイル1枚が画面で何pxか */
data class TileRange(val z: Int, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val tilePx: Double) {
    val count: Int get() = (maxX - minX + 1) * (maxY - minY + 1)
}
