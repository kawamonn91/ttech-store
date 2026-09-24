package com.ttech.weightlog.domain

import kotlinx.serialization.Serializable

@Serializable
data class WeightEntry(
    val id: String,
    /** ISO8601 (YYYY-MM-DD)。文字列のままでも辞書順ソートが日付順と一致する。 */
    val date: String,
    val weightKg: Double,
    val bodyFatPercent: Double?,
)

/** 新しい記録を先頭に足し、日付の新しい順に並べ直す(同じ日は後から記録したものが先)。 */
fun List<WeightEntry>.addEntry(entry: WeightEntry): List<WeightEntry> =
    (listOf(entry) + this).sortedByDescending { it.date }

/** グラフ用の体重の並び(古い記録が先頭、同じ日は先に記録したものが先)。 */
fun List<WeightEntry>.weightPointsAscending(): List<Double> =
    reversed().sortedBy { it.date }.map { it.weightKg }

data class ChartPoint(val x: Float, val y: Float)

/**
 * 折れ線グラフの座標を計算する(Webアプリ版の LineChart コンポーネントと同じロジック。320x100の座標系)。
 * 値が2点未満なら描けないので空リストを返す。
 */
fun chartPoints(values: List<Double>, width: Float = 320f, height: Float = 100f): List<ChartPoint> {
    if (values.size < 2) return emptyList()
    val min = values.min()
    val max = values.max()
    val range = (max - min).let { if (it == 0.0) 1.0 else it }
    val stepX = width / (values.size - 1)
    return values.mapIndexed { i, v ->
        ChartPoint(i * stepX, (height - ((v - min) / range) * (height - 10) - 5).toFloat())
    }
}

/** JSの数値表示と同じく、整数なら小数点以下を付けない(72.0 → "72"、72.5 → "72.5")。 */
fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
