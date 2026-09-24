package com.ttech.movielog.domain

import kotlinx.serialization.Serializable

/** 作品の種類(Webアプリ版と同じ2種)。 */
val MOVIE_KINDS = listOf("映画", "ドラマ")

@Serializable
data class Movie(
    val id: String,
    val title: String,
    /** "映画" または "ドラマ" */
    val kind: String,
    /** 評価 1〜5 */
    val rating: Int,
    /** 視聴日 ISO8601 (YYYY-MM-DD) */
    val watchedDate: String,
    val memo: String,
)

/** 入力値から記録を作る。作品名が空なら作らない(null)。作品名・感想は前後の空白を除く。 */
fun buildMovie(id: String, title: String, kind: String, rating: Int, watchedDate: String, memo: String): Movie? {
    if (title.isBlank()) return null
    return Movie(id, title.trim(), kind, rating.coerceIn(1, 5), watchedDate, memo.trim())
}

/** 一覧の見出し(「[映画] タイトル」)。 */
fun Movie.heading(): String = "[$kind] $title"

/** 一覧の補足行の末尾(感想があるときだけ「 ・ 感想」)。 */
fun Movie.memoSuffix(): String = if (memo.isNotEmpty()) " ・ $memo" else ""
