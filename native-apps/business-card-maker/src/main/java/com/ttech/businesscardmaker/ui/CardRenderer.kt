package com.ttech.businesscardmaker.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.ttech.businesscardmaker.domain.CardTheme

/**
 * 名刺プレビューと共有用Bitmapの両方から同じ描画結果になるよう、
 * android.graphics.Canvas への描画をここに1本化する。
 * (Webアプリ版のCanvas 2D描画ロジックと同じレイアウト: 幅600x高さ340相当の91mm x 55mm名刺比率)
 */
fun drawBusinessCard(
    canvas: Canvas,
    width: Float,
    height: Float,
    name: String,
    title: String,
    company: String,
    contact: String,
    theme: CardTheme,
) {
    val scale = width / 600f

    canvas.drawColor(theme.background.toInt())

    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accent.toInt() }
    canvas.drawRect(0f, 0f, 10f * scale, height, accentPaint)

    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.foreground.toInt()
        textSize = 34f * scale
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText(name, 48f * scale, 140f * scale, namePaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.accent.toInt()
        textSize = 18f * scale
    }
    canvas.drawText(title, 48f * scale, 172f * scale, titlePaint)

    val companyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.foreground.toInt()
        textSize = 20f * scale
    }
    canvas.drawText(company, 48f * scale, 230f * scale, companyPaint)

    val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.foreground.toInt()
        textSize = 16f * scale
    }
    canvas.drawText(contact, 48f * scale, 270f * scale, contactPaint)
}
