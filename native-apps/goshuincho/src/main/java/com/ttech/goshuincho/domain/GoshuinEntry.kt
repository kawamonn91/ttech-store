package com.ttech.goshuincho.domain

import kotlinx.serialization.Serializable

@Serializable
data class GoshuinEntry(
    val id: String,
    val name: String,
    val prefecture: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val memo: String,
)

fun isValidEntry(name: String): Boolean = name.isNotBlank()

data class PrefectureCount(val prefecture: String, val count: Int)

/**
 * 都道府県別の参拝数(都道府県が未入力の記録は含めない)。
 * Webアプリ版には無い集計だが、初めて登場した都道府県の順に並べる(fishing-log の魚種別内訳と同じ方針)。
 */
fun List<GoshuinEntry>.countByPrefecture(): List<PrefectureCount> {
    val counts = LinkedHashMap<String, Int>()
    for (e in this) {
        if (e.prefecture.isBlank()) continue
        counts[e.prefecture] = (counts[e.prefecture] ?: 0) + 1
    }
    return counts.map { (prefecture, count) -> PrefectureCount(prefecture, count) }
}
