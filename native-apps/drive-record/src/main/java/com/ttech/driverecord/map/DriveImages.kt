package com.ttech.driverecord.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.ttech.driverecord.domain.DriveSummary
import com.ttech.track.data.TrackFiles
import com.ttech.track.domain.Format
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.RouteSegments
import com.ttech.track.domain.TrackPoint
import com.ttech.track.map.RouteImageRenderer
import com.ttech.track.map.RouteStyle
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ドライブごとの、地図の上に赤いルートを描いた画像。一覧のサムネイルと、共有する画像に使う。
 * 出来上がった画像は端末に保存し、二度目からは作り直さない。
 * 地図が取得できなかった(通信できなかった)ときは保存せず、次に開いたときに作り直す。
 */
class DriveImages(private val files: TrackFiles, private val renderer: RouteImageRenderer) {
    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        private const val DENSITY = 2.6f
    }

    /** 保存済みの画像を返す。無ければ作って保存する。作れなかったら null */
    suspend fun ensure(summary: DriveSummary, points: List<TrackPoint>, dark: Boolean): Boolean {
        if (files.hasImage(summary.id)) return true
        val result = render(summary, points, dark) ?: return false
        if (!result.tilesLoaded) return false
        save(summary.id, result.bitmap)
        return true
    }

    /** 共有用に、いま作る(地図が取れなくても、ルートだけの画像を返す) */
    suspend fun render(summary: DriveSummary, points: List<TrackPoint>, dark: Boolean): RouteImageRenderer.Result? {
        val route = RouteSegments.from(points)
        if (route.isEmpty) return null
        return renderer.render(
            route = route,
            mapStyle = if (dark) MapStyle.Dark else MapStyle.Light,
            routeStyle = RouteStyle.Red,
            width = WIDTH,
            height = HEIGHT,
            paddingPx = 120,
            density = DENSITY,
            bottomInsetPx = 470,
            overlay = { canvas, _ -> drawOverlay(canvas, summary, dark) },
        )
    }

    suspend fun regenerate(summary: DriveSummary, points: List<TrackPoint>, dark: Boolean): Boolean {
        files.imageFile(summary.id).delete()
        return ensure(summary, points, dark)
    }

    private suspend fun save(id: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        FileOutputStream(files.imageFile(id)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    /** 一覧用に、小さく読み込む */
    suspend fun thumbnail(id: String, maxSide: Int = 720): Bitmap? = withContext(Dispatchers.IO) {
        val file = files.imageFile(id)
        if (!file.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** 画像の下に重ねる、日時・距離・時間・速度のカード */
    private fun drawOverlay(canvas: Canvas, s: DriveSummary, dark: Boolean) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val margin = 44f
        val panel = RectF(margin, h - 440f, w - margin, h - margin - 22f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) Color.argb(214, 10, 14, 20) else Color.argb(226, 255, 255, 255) }
        canvas.drawRoundRect(panel, 40f, 40f, bg)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = if (dark) Color.argb(60, 255, 255, 255) else Color.argb(40, 0, 0, 0)
        }
        canvas.drawRoundRect(panel, 40f, 40f, edge)

        val strong = if (dark) Color.WHITE else Color.parseColor("#111827")
        val soft = if (dark) Color.parseColor("#9AA4B2") else Color.parseColor("#6B7280")
        fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val left = panel.left + 44f

        // 日時と、ブランド名
        val range = "${Format.date(s.startTimeMs)}  ${Format.time(s.startTimeMs)} → ${Format.time(s.endTimeMs)}"
        canvas.drawText(range, left, panel.top + 66f, paint(34f, soft))
        val brand = "ドライブ記録"
        val brandPaint = paint(28f, soft)
        canvas.drawText(brand, panel.right - 44f - brandPaint.measureText(brand), panel.top + 66f, brandPaint)

        // 距離(大きく)
        val distNum = Format.distanceKmNumber(s.distanceM)
        val bigPaint = paint(150f, strong, bold = true)
        canvas.drawText(distNum, left, panel.top + 214f, bigPaint)
        canvas.drawText("km", left + bigPaint.measureText(distNum) + 16f, panel.top + 214f, paint(52f, soft, bold = true))

        // 時間・平均速度・最高速度
        val colW = (panel.width() - 88f) / 3f
        val stats = listOf(
            "時間" to Format.duration(s.durationMs),
            "平均" to Format.speedKmh(s.avgMovingSpeedMps),
            "最高" to Format.speedKmh(s.maxSpeedMps),
        )
        stats.forEachIndexed { i, (label, value) ->
            val x = left + colW * i
            canvas.drawText(label, x, panel.top + 292f, paint(30f, soft))
            canvas.drawText(value, x, panel.top + 344f, paint(44f, strong, bold = true))
        }
        // 出発地 → 到着地
        val from = s.startLabel
        val to = s.endLabel
        if (from != null || to != null) {
            val line = "${from ?: "出発地"}  →  ${to ?: "到着地"}"
            val linePaint = paint(30f, soft)
            var text = line
            while (linePaint.measureText(text) > panel.width() - 88f && text.length > 4) text = text.dropLast(2)
            if (text != line) text += "…"
            canvas.drawText(text, left, panel.bottom - 30f, linePaint)
        }
    }
}
