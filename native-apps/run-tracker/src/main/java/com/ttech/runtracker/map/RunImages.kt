package com.ttech.runtracker.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.ttech.runtracker.domain.RunSummary
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
 * ランごとの、地図の上にオレンジのルートを描いた画像。一覧のサムネイルと、共有する画像に使う。
 * 出来上がった画像は端末に保存し、二度目からは作り直さない。
 * 地図が取得できなかった(通信できなかった)ときは保存せず、次に開いたときに作り直す。
 */
class RunImages(private val files: TrackFiles, private val renderer: RouteImageRenderer) {
    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        private const val DENSITY = 2.6f
    }

    /** 保存済みの画像があるか。無ければ作って保存する。作れなかったら false */
    suspend fun ensure(summary: RunSummary, points: List<TrackPoint>, dark: Boolean): Boolean {
        if (files.hasImage(summary.id)) return true
        val result = render(summary, points, dark) ?: return false
        if (!result.tilesLoaded) return false
        save(summary.id, result.bitmap)
        return true
    }

    /** 共有用に、いま作る(地図が取れなくても、ルートだけの画像を返す) */
    suspend fun render(summary: RunSummary, points: List<TrackPoint>, dark: Boolean): RouteImageRenderer.Result? {
        val route = RouteSegments.from(points)
        if (route.isEmpty) return null
        return renderer.render(
            route = route,
            mapStyle = if (dark) MapStyle.Dark else MapStyle.Light,
            routeStyle = RouteStyle.Orange,
            width = WIDTH,
            height = HEIGHT,
            paddingPx = 120,
            density = DENSITY,
            bottomInsetPx = 520,
            overlay = { canvas, _ -> drawOverlay(canvas, summary, dark) },
        )
    }

    suspend fun regenerate(summary: RunSummary, points: List<TrackPoint>, dark: Boolean): Boolean {
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

    /** 画像の下に重ねる、タイトル・日時・距離・時間・ペースのカード */
    private fun drawOverlay(canvas: Canvas, s: RunSummary, dark: Boolean) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val margin = 44f
        val panel = RectF(margin, h - 500f, w - margin, h - margin - 22f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) Color.argb(214, 14, 12, 10) else Color.argb(226, 255, 255, 255) }
        canvas.drawRoundRect(panel, 40f, 40f, bg)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = if (dark) Color.argb(60, 255, 255, 255) else Color.argb(40, 0, 0, 0)
        }
        canvas.drawRoundRect(panel, 40f, 40f, edge)

        val strong = if (dark) Color.WHITE else Color.parseColor("#111827")
        val soft = if (dark) Color.parseColor("#A8A29E") else Color.parseColor("#6B7280")
        val accent = Color.parseColor("#FC4C02")
        fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val left = panel.left + 44f

        // タイトルと、ブランド名
        canvas.drawText(s.displayTitle, left, panel.top + 68f, paint(42f, strong, bold = true))
        val brand = "ランニング記録"
        val brandPaint = paint(28f, accent, bold = true)
        canvas.drawText(brand, panel.right - 44f - brandPaint.measureText(brand), panel.top + 66f, brandPaint)
        canvas.drawText("${Format.date(s.startTimeMs)}  ${Format.time(s.startTimeMs)}", left, panel.top + 112f, paint(30f, soft))

        // 距離(大きく)
        val distNum = Format.distanceKmNumber(s.distanceM)
        val bigPaint = paint(150f, strong, bold = true)
        canvas.drawText(distNum, left, panel.top + 244f, bigPaint)
        canvas.drawText("km", left + bigPaint.measureText(distNum) + 16f, panel.top + 244f, paint(52f, soft, bold = true))

        // 時間・ペース・獲得標高
        val colW = (panel.width() - 88f) / 3f
        val stats = listOf(
            "時間" to Format.clock(s.movingMs),
            "ペース" to Format.pace(s.avgPaceSecPerKm),
            "獲得標高" to "${Math.round(s.elevationGainM)} m",
        )
        stats.forEachIndexed { i, (label, value) ->
            val x = left + colW * i
            canvas.drawText(label, x, panel.top + 306f, paint(28f, soft))
            canvas.drawText(value, x, panel.top + 352f, paint(40f, strong, bold = true))
        }
        // 出発地
        val place = s.startLabel
        if (place != null) {
            val linePaint = paint(28f, soft)
            var text: String = place
            while (linePaint.measureText(text) > panel.width() - 88f && text.length > 4) text = text.dropLast(2)
            if (text != place) text += "…"
            canvas.drawText(text, left, panel.bottom - 30f, linePaint)
        }
    }
}
