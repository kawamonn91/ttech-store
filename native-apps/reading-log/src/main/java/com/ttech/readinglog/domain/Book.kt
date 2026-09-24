package com.ttech.readinglog.domain

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val pages: Int,
    /** 評価 1〜5 */
    val rating: Int,
    /** 読了日 ISO8601 (YYYY-MM-DD) */
    val finishedDate: String,
)

data class YearStats(val count: Int, val totalPages: Int)

/** [year](例: "2026")に読了した冊数と総ページ数。 */
fun List<Book>.statsForYear(year: String): YearStats {
    val yearBooks = filter { it.finishedDate.startsWith(year) }
    return YearStats(yearBooks.size, yearBooks.sumOf { it.pages })
}

/** 一覧の補足行の先頭部分(「著者 ・ 320p ・ 」)。著者が空・ページ数0の項目は出さない。 */
fun Book.detailPrefix(): String = buildString {
    if (author.isNotEmpty()) append("$author ・ ")
    if (pages > 0) append("${pages}p ・ ")
}
