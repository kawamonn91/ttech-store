package com.ttech.imagebatch.domain

data class SizePreset(val label: String, val width: Int, val height: Int)

val SIZE_PRESETS = listOf(
    SizePreset("800×800", 800, 800),
    SizePreset("1200×1200", 1200, 1200),
    SizePreset("1080×1350(縦長)", 1080, 1350),
)

val COLOR_PRESETS = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF5F5F0.toInt(), 0xFFE2E8F0.toInt())

data class DrawRect(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * 元画像([srcWidth]x[srcHeight])を、アスペクト比を保ったまま
 * [targetWidth]x[targetHeight] のキャンバス中央に収める矩形を求める(Webのcanvas版と同じロジック)。
 */
fun computeDrawRect(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): DrawRect {
    val scale = minOf(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight)
    val drawWidth = srcWidth * scale
    val drawHeight = srcHeight * scale
    return DrawRect(
        x = (targetWidth - drawWidth) / 2,
        y = (targetHeight - drawHeight) / 2,
        width = drawWidth,
        height = drawHeight,
    )
}
