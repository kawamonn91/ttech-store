package com.ttech.cookingunits.domain

import kotlin.math.round

/** 材料の重さ(密度, g/ml)。一般的な目安値(メーカーや状態により前後する)。 */
val IngredientDensityGPerMl: LinkedHashMap<String, Double> = linkedMapOf(
    "水" to 1.0,
    "牛乳" to 1.03,
    "砂糖(上白糖)" to 0.9,
    "塩" to 1.2,
    "小麦粉(薄力粉)" to 0.55,
    "バター" to 0.9,
    "サラダ油" to 0.92,
    "醤油" to 1.2,
    "みそ" to 1.2,
    "はちみつ" to 1.4,
)

/** 体積の単位から ml への換算係数。 */
val VolumeUnitMl: LinkedHashMap<String, Double> = linkedMapOf(
    "ml" to 1.0,
    "カップ(200ml)" to 200.0,
    "大さじ(15ml)" to 15.0,
    "小さじ(5ml)" to 5.0,
)

/** 料理の単位換算(体積↔重さ)の純粋ロジック。Webのmicrosaas版と同じ目安値・計算式を使う。 */
object CookingUnits {
    /** [amount] × [unit] を ml に換算する。未知の単位は ml 扱い(=そのまま)にする。 */
    fun toMl(amount: Double, unit: String): Double = amount * (VolumeUnitMl[unit] ?: 1.0)

    /** [ml] を [ingredient] の重さ(g)に換算する。小数第1位に丸める。未知の材料は水(密度1.0)扱い。 */
    fun toGrams(ml: Double, ingredient: String): Double {
        val density = IngredientDensityGPerMl[ingredient] ?: 1.0
        return round(ml * density * 10) / 10
    }
}
