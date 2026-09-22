package com.ttech.childgrowth.domain

import kotlinx.serialization.Serializable

@Serializable
data class GrowthEntry(
    val id: String,
    /** ISO8601 (YYYY-MM-DD)。文字列のままでも辞書順ソートが日付順と一致する。 */
    val date: String,
    val heightCm: Double,
    val weightKg: Double,
    /**
     * 記録した実時刻(epoch ms)。同じ日に複数回記録した場合、date だけでは
     * 順序が一意に決まらずグラフの並びが不安定になる(実機で確認した不具合)ため、
     * 同日タイブレークとして使う。
     */
    val recordedAt: Long,
)

/** 記録一覧の表示順(新しい記録が先頭)。 */
fun List<GrowthEntry>.sortedByDateDescending(): List<GrowthEntry> =
    sortedWith(compareByDescending<GrowthEntry> { it.date }.thenByDescending { it.recordedAt })

/** グラフ描画用の表示順(古い記録が先頭)。 */
fun List<GrowthEntry>.sortedByDateAscending(): List<GrowthEntry> =
    sortedWith(compareBy<GrowthEntry> { it.date }.thenBy { it.recordedAt })

data class ChartPoint(val x: Float, val y: Float)

/**
 * 折れ線グラフの座標を計算する(Webアプリ版の LineChart コンポーネントと同じロジック)。
 * 値が2点未満なら描けないので空リストを返す。
 */
fun chartPoints(values: List<Double>, width: Float = 320f, height: Float = 80f): List<ChartPoint> {
    if (values.size < 2) return emptyList()
    val min = values.min()
    val max = values.max()
    val range = (max - min).let { if (it == 0.0) 1.0 else it }
    val stepX = width / (values.size - 1)
    return values.mapIndexed { i, v ->
        val x = i * stepX
        val y = (height - ((v - min) / range) * (height - 10) - 5).toFloat()
        ChartPoint(x, y)
    }
}
