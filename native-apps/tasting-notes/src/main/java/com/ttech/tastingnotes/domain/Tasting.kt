package com.ttech.tastingnotes.domain

import kotlinx.serialization.Serializable

/** お酒の種類(Webアプリ版と同じ3種)。 */
val TASTING_KINDS = listOf("ワイン", "日本酒", "その他")

@Serializable
data class Tasting(
    val id: String,
    val name: String,
    /** "ワイン" / "日本酒" / "その他" */
    val kind: String,
    /** 評価 1〜5 */
    val rating: Int,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val notes: String,
)

/** 入力値から記録を作る。銘柄名が空なら作らない(null)。銘柄名・メモは前後の空白を除く。 */
fun buildTasting(id: String, name: String, kind: String, rating: Int, date: String, notes: String): Tasting? {
    if (name.isBlank()) return null
    return Tasting(id, name.trim(), kind, rating.coerceIn(1, 5), date, notes.trim())
}

/** 一覧の見出し(「[ワイン] 銘柄名」)。 */
fun Tasting.heading(): String = "[$kind] $name"

