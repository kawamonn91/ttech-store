package com.ttech.pdftoolkit.domain

private val RANGE_PART = Regex("""^(\d+)(?:-(\d+))?$""")

/** 結合には2つ以上のPDFが必要。 */
fun canMerge(fileCount: Int): Boolean = fileCount >= 2

/** 抽出できるのは、PDFを選んでいて、ページ指定が空でないとき。 */
fun canExtract(hasFile: Boolean, range: String): Boolean = hasFile && range.isNotBlank()

/**
 * 「1-3,5」のようなページ指定を、0始まりのページ番号の並びにする。
 * 書式に合わない部分は無視し、総ページ数[pageCount]を超える分は切り捨てる。
 * 入力した順にそのまま並べる(同じページを2回指定すれば2回入る)。
 * Web版と違い、0ページ目の指定は無効として捨てる(Web版では -1 番目のページ指定になってしまうため)。
 */
fun parsePageRange(input: String, pageCount: Int): List<Int> {
    val indices = mutableListOf<Int>()
    for (part in input.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
        val match = RANGE_PART.matchEntire(part) ?: continue
        // 桁数が多すぎてLongに収まらない指定は、書式に合わないものとして無視する。
        val start = match.groupValues[1].toLongOrNull() ?: continue
        val endText = match.groupValues[2]
        val end = if (endText.isEmpty()) start else endText.toLongOrNull() ?: continue
        var page = maxOf(start, 1L)
        while (page <= end && page <= pageCount) {
            indices.add((page - 1).toInt())
            page++
        }
    }
    return indices
}
