package com.ttech.track.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ttech.track.domain.LatLon
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.MapViewport
import com.ttech.track.domain.RouteSegments
import com.ttech.track.domain.TileUrls
import com.ttech.track.domain.WebMercator

/** ルートの線の見た目。中心の線のまわりに、光がにじむような線を重ねる */
data class RouteStyle(
    val coreColor: Int,
    val glowColor: Int,
    val coreWidthDp: Float = 3.5f,
    val glow: Boolean = true,
) {
    companion object {
        /** ドライブ用: 赤く光る線 */
        val Red = RouteStyle(coreColor = Color.parseColor("#FF453A"), glowColor = Color.parseColor("#FF1744"), coreWidthDp = 3.5f)

        /** ランニング用: オレンジの線(ストラバ風) */
        val Orange = RouteStyle(coreColor = Color.parseColor("#FC4C02"), glowColor = Color.parseColor("#FC4C02"), coreWidthDp = 4f)
    }
}

/**
 * 地図とルートを Canvas に描く。画面(Compose の drawIntoCanvas)と、書き出す画像(Bitmap)の
 * どちらも同じ関数で描くので、見た目が一致する。
 */
object MapPainter {
    fun backgroundColor(style: MapStyle): Int = when (style) {
        MapStyle.Dark -> Color.parseColor("#0F1216")
        MapStyle.Light -> Color.parseColor("#EEEDE9")
    }

    fun drawBackground(canvas: Canvas, style: MapStyle) = canvas.drawColor(backgroundColor(style))

    /**
     * ダーク表示用の色の変換。明るい地図(OpenStreetMap)の色を反転し、色相を半回転して(草地は緑のまま、水は青のまま)
     * 暗い地図にする。さらに彩度と明るさを少し下げて、上に描く赤いルートが際立つようにする。
     */
    private val darkFilter: ColorMatrixColorFilter by lazy {
        val invert = ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
        val hueHalfTurn = ColorMatrix(floatArrayOf(-0.574f, 1.430f, 0.144f, 0f, 0f, 0.426f, 0.430f, 0.144f, 0f, 0f, 0.426f, 1.430f, -0.856f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        val m = ColorMatrix()
        m.postConcat(invert)
        m.postConcat(hueHalfTurn)
        m.postConcat(ColorMatrix().apply { setSaturation(0.5f) })
        m.postConcat(ColorMatrix(floatArrayOf(0.9f, 0f, 0f, 0f, -6f, 0f, 0.9f, 0f, 0f, -6f, 0f, 0f, 0.9f, 0f, -3f, 0f, 0f, 0f, 1f, 0f)))
        ColorMatrixColorFilter(m)
    }

    /** 表示範囲に入るタイルを描く。まだ取得できていないタイルは、背景のまま */
    fun drawTiles(canvas: Canvas, vp: MapViewport, style: MapStyle, tile: (z: Int, x: Int, y: Int) -> Bitmap?) {
        val range = vp.visibleTiles()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { if (style == MapStyle.Dark) colorFilter = darkFilter }
        val n = 1 shl range.z
        for (y in range.minY..range.maxY) {
            if (y < 0 || y >= n) continue
            for (x in range.minX..range.maxX) {
                val bmp = tile(range.z, x, y) ?: continue
                val left = vp.toScreenX(WebMercator.lon(x * WebMercator.TILE, range.z.toDouble())).toFloat()
                val right = vp.toScreenX(WebMercator.lon((x + 1) * WebMercator.TILE, range.z.toDouble())).toFloat()
                val top = vp.toScreenY(WebMercator.lat(y * WebMercator.TILE, range.z.toDouble())).toFloat()
                val bottom = vp.toScreenY(WebMercator.lat((y + 1) * WebMercator.TILE, range.z.toDouble())).toFloat()
                // タイルの継ぎ目に細い線が出ないように、ほんの少し重ねる
                canvas.drawBitmap(bmp, null, RectF(left - 0.5f, top - 0.5f, right + 0.5f, bottom + 0.5f), paint)
            }
        }
    }

    fun drawRoute(canvas: Canvas, vp: MapViewport, route: RouteSegments, style: RouteStyle, density: Float) {
        if (route.isEmpty) return
        val core = style.coreWidthDp * density
        val paths = route.solid.filter { it.size >= 2 }.map { path(vp, it) }
        val singles = route.solid.filter { it.size == 1 }

        fun stroke(width: Float, color: Int, alpha: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = width
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                this.color = color
                this.alpha = alpha
            }
            for (path in paths) canvas.drawPath(path, p)
        }

        if (style.glow) {
            stroke(core * 6f, style.glowColor, 34)
            stroke(core * 3.2f, style.glowColor, 80)
        }
        stroke(core * 1.5f, Color.BLACK, 90) // 地図の上で線がくっきり見えるよう、うっすら縁取る
        stroke(core, style.coreColor, 255)
        if (style.glow) stroke(core * 0.35f, Color.WHITE, 150)

        // 途切れた区間は、測っていないので点線で「つないでいるだけ」と分かるようにする
        if (route.gaps.isNotEmpty()) {
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = core * 0.8f
                color = style.coreColor
                alpha = 150
                pathEffect = DashPathEffect(floatArrayOf(core * 2.5f, core * 2.5f), 0f)
            }
            for ((a, b) in route.gaps) {
                canvas.drawLine(vp.toScreenX(a.lon).toFloat(), vp.toScreenY(a.lat).toFloat(), vp.toScreenX(b.lon).toFloat(), vp.toScreenY(b.lat).toFloat(), dash)
            }
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.coreColor }
        for (s in singles) canvas.drawCircle(vp.toScreenX(s[0].lon).toFloat(), vp.toScreenY(s[0].lat).toFloat(), core, dot)
    }

    private fun path(vp: MapViewport, points: List<LatLon>): Path {
        val path = Path()
        var first = true
        for (p in points) {
            val x = vp.toScreenX(p.lon).toFloat()
            val y = vp.toScreenY(p.lat).toFloat()
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
        return path
    }

    /** 出発地(緑の丸)と到着地(白黒の旗のような丸) */
    fun drawMarkers(canvas: Canvas, vp: MapViewport, start: LatLon?, goal: LatLon?, density: Float) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
        start?.let {
            val x = vp.toScreenX(it.lon).toFloat()
            val y = vp.toScreenY(it.lat).toFloat()
            canvas.drawCircle(x, y, 9.5f * density, shadow)
            canvas.drawCircle(x, y, 8f * density, ring)
            canvas.drawCircle(x, y, 5.2f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22C55E") })
        }
        goal?.let {
            val x = vp.toScreenX(it.lon).toFloat()
            val y = vp.toScreenY(it.lat).toFloat()
            val r = 9f * density
            canvas.drawCircle(x, y, r + 1.5f * density, shadow)
            canvas.drawCircle(x, y, r, ring)
            // 市松模様(ゴール旗)
            val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111111") }
            val s = r * 0.62f
            canvas.save()
            canvas.clipPath(Path().apply { addCircle(x, y, r * 0.86f, Path.Direction.CW) })
            canvas.drawRect(x - s, y - s, x, y, black)
            canvas.drawRect(x, y, x + s, y + s, black)
            canvas.restore()
        }
    }

    fun drawAttribution(canvas: Canvas, width: Int, height: Int, density: Float, style: MapStyle) {
        val text = TileUrls.ATTRIBUTION
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f * density
            color = if (style == MapStyle.Dark) Color.argb(200, 220, 220, 220) else Color.argb(220, 60, 60, 60)
        }
        val w = paint.measureText(text)
        val pad = 4f * density
        val bg = Paint().apply { color = if (style == MapStyle.Dark) Color.argb(110, 0, 0, 0) else Color.argb(150, 255, 255, 255) }
        val right = width - 6f * density
        val baseline = height - 6f * density
        canvas.drawRect(right - w - pad, baseline - 9f * density - pad / 2, right + pad, baseline + pad, bg)
        canvas.drawText(text, right - w, baseline, paint)
    }
}
