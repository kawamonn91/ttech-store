package com.ttech.sheetmusicviewer.domain

import kotlin.math.max

/** 最後のページより先には進まない。 */
fun nextPageIndex(index: Int, pageCount: Int): Int = minOf(index + 1, pageCount - 1)

/** 最初のページより前には戻らない。 */
fun prevPageIndex(index: Int): Int = maxOf(index - 1, 0)

fun pageLabel(index: Int, pageCount: Int): String = "${index + 1} / $pageCount"

enum class PageTurn { PREV, NEXT }

/** 画面の左半分をタップすれば前へ、右半分なら次へ。 */
fun turnForTap(x: Float, width: Float): PageTurn = if (x < width / 2f) PageTurn.PREV else PageTurn.NEXT

/**
 * 大きな写真をそのまま読み込むとメモリが足りなくなるため、長辺が[maxDimension]を下回らない範囲で
 * 最大限に縮小して読み込む倍率(2のべき乗)を求める。
 */
fun calcSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    val longSide = max(width, height)
    var sample = 1
    while (longSide / (sample * 2) >= maxDimension) sample *= 2
    return sample
}

/** EXIFの向き情報から、正しい向きに直すための回転角(度)を求める。反転を伴う向きは対象外。 */
fun exifRotationDegrees(orientation: Int): Int = when (orientation) {
    6 -> 90 // ORIENTATION_ROTATE_90
    3 -> 180 // ORIENTATION_ROTATE_180
    8 -> 270 // ORIENTATION_ROTATE_270
    else -> 0
}
