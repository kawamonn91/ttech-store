package com.ttech.tripshiori.share

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ttech.tripshiori.domain.Block
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.buildDocument
import java.io.OutputStream

/**
 * しおりをA4のPDFにする。[buildDocument] と同じ内容を、表紙・見出し・時刻つきの予定・チェック欄の形で描く。
 * 日本語の文字は端末のフォントで描くので、フォントを同梱しない。
 */
object TripPdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val ACCENT = 0xFF0E7490.toInt()

    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    private fun paint(size: Float, color: Int = Color.parseColor("#1F2937"), typeface: Typeface = Typeface.DEFAULT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.typeface = typeface
        }

    fun write(trip: Trip, out: OutputStream) {
        val doc = PdfDocument()
        try {
            Writer(doc).render(buildDocument(trip))
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private class Writer(private val doc: PdfDocument) {
        private var pageNo = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        private val title = paint(24f, Color.WHITE, bold)
        private val subtitle = paint(12f, Color.parseColor("#E0F2FE"))
        private val heading = paint(14f, ACCENT, bold)
        private val lead = paint(11f, ACCENT, bold)
        private val body = paint(12f)
        private val detail = paint(10f, Color.parseColor("#6B7280"))
        private val footer = paint(9f, Color.parseColor("#9CA3AF"))
        private val rule = Paint().apply { color = Color.parseColor("#BAE6FD"); strokeWidth = 1.2f }
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280"); style = Paint.Style.STROKE; strokeWidth = 1.2f }
        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND }

        private val contentW = PAGE_W - MARGIN * 2
        private val leadW = 46f

        fun render(blocks: List<Block>) {
            newPage()
            for (b in blocks) {
                when (b) {
                    is Block.Title -> drawTitle(b)
                    is Block.Heading -> drawHeading(b.text)
                    is Block.Row -> drawRow(b)
                    is Block.Check -> drawCheck(b)
                    is Block.Line -> drawLines(wrap(b.text, body, contentW), body, MARGIN, 4f)
                }
            }
            finishPage()
        }

        private fun newPage() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = MARGIN
        }

        private fun finishPage() {
            canvas.drawText("旅のしおり  $pageNo", MARGIN, PAGE_H - 20f, footer)
            doc.finishPage(page)
        }

        /** 残りが足りなければ次のページへ */
        private fun ensure(height: Float) {
            if (y + height > PAGE_H - MARGIN) {
                finishPage()
                newPage()
            }
        }

        private fun wrap(text: String, p: Paint, width: Float): List<String> {
            val lines = mutableListOf<String>()
            for (paragraph in text.split('\n')) {
                var rest = paragraph
                if (rest.isEmpty()) lines += ""
                while (rest.isNotEmpty()) {
                    val n = p.breakText(rest, true, width, null).coerceAtLeast(1)
                    lines += rest.substring(0, n)
                    rest = rest.substring(n)
                }
            }
            return lines
        }

        private fun lineHeight(p: Paint) = p.textSize * 1.45f

        private fun drawLines(lines: List<String>, p: Paint, x: Float, after: Float) {
            for (line in lines) {
                ensure(lineHeight(p))
                y += lineHeight(p)
                canvas.drawText(line, x, y - p.textSize * 0.3f, p)
            }
            y += after
        }

        private fun drawTitle(b: Block.Title) {
            val titleLines = wrap(b.text, title, contentW - 28f)
            val subLines = b.subtitle.flatMap { wrap(it, subtitle, contentW - 28f) }
            val h = 20f + titleLines.size * lineHeight(title) + subLines.size * lineHeight(subtitle) + 14f
            ensure(h)
            val bg = Paint().apply { color = ACCENT }
            canvas.drawRoundRect(MARGIN, y, MARGIN + contentW, y + h, 10f, 10f, bg)
            var ty = y + 14f
            for (l in titleLines) {
                ty += lineHeight(title)
                canvas.drawText(l, MARGIN + 14f, ty - title.textSize * 0.3f, title)
            }
            ty += 4f
            for (l in subLines) {
                ty += lineHeight(subtitle)
                canvas.drawText(l, MARGIN + 14f, ty - subtitle.textSize * 0.3f, subtitle)
            }
            y += h + 10f
        }

        private fun drawHeading(text: String) {
            ensure(lineHeight(heading) + 100f) // 見出しだけがページの末尾に残らないように(続く項目1つぶんの余白を確保)
            y += 12f
            y += lineHeight(heading)
            canvas.drawText(text, MARGIN, y - heading.textSize * 0.3f, heading)
            y += 4f
            canvas.drawLine(MARGIN, y, MARGIN + contentW, y, rule)
            y += 6f
        }

        private fun drawRow(b: Block.Row) {
            val textLines = wrap(b.text, body, contentW - leadW)
            val detailLines = b.details.flatMap { wrap(it, detail, contentW - leadW - 8f) }
            val h = textLines.size * lineHeight(body) + detailLines.size * lineHeight(detail) + 6f
            ensure(minOf(h, 90f))
            val top = y
            canvas.drawText(b.lead, MARGIN, top + lineHeight(body) - body.textSize * 0.3f, lead)
            for (l in textLines) {
                ensure(lineHeight(body))
                y += lineHeight(body)
                canvas.drawText(l, MARGIN + leadW, y - body.textSize * 0.3f, body)
            }
            for (l in detailLines) {
                ensure(lineHeight(detail))
                y += lineHeight(detail)
                canvas.drawText(l, MARGIN + leadW + 8f, y - detail.textSize * 0.3f, detail)
            }
            y += 6f
        }

        private fun drawCheck(b: Block.Check) {
            val lines = wrap(b.text, body, contentW - 24f)
            ensure(lineHeight(body))
            val size = 11f
            val top = y + (lineHeight(body) - size) / 2f
            canvas.drawRect(MARGIN, top, MARGIN + size, top + size, box)
            if (b.checked) {
                canvas.drawLine(MARGIN + 2.5f, top + 6f, MARGIN + 5f, top + 8.5f, tick)
                canvas.drawLine(MARGIN + 5f, top + 8.5f, MARGIN + 9.5f, top + 2.5f, tick)
            }
            drawLines(lines, body, MARGIN + 20f, 2f)
        }
    }
}
