package com.ttech.pdftoolkit.domain

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.InputStream
import java.io.OutputStream

/**
 * 「1-3,5」形式のページ指定を、0始まりのページ番号の並びにする(Webアプリ版と同じ書式)。
 * 読めない部分は無視し、[max] を超えるページ・0以下のページは含めない。指定した順・重複はそのまま残す。
 */
fun parseRange(input: String, max: Int): List<Int> {
    val indices = mutableListOf<Int>()
    for (part in input.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
        val m = Regex("""^(\d+)(?:-(\d+))?$""").matchEntire(part) ?: continue
        val start = m.groupValues[1].toIntOrNull() ?: continue
        val end = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
        for (p in maxOf(start, 1)..minOf(end, max)) indices.add(p - 1)
    }
    return indices
}

/** PDFのページ数。 */
fun pageCount(input: InputStream): Int = PDDocument.load(input).use { it.numberOfPages }

/**
 * 複数のPDFを渡した順に1つへ結合して [output] に書き出す。
 * 大きなPDFでもメモリを使い切らないよう、作業領域は一時ファイルにする。
 */
fun mergePdfs(inputs: List<InputStream>, output: OutputStream) {
    val merger = PDFMergerUtility()
    inputs.forEach(merger::addSource)
    merger.destinationStream = output
    merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
}

/** [pageIndices](0始まり)のページだけを、その順に抜き出したPDFを [output] に書き出す。 */
fun extractPages(input: InputStream, pageIndices: List<Int>, output: OutputStream) {
    PDDocument.load(input).use { source ->
        PDDocument().use { result ->
            pageIndices.forEach { result.importPage(source.getPage(it)) }
            // importPage はページを複製せず参照するため、元文書を閉じる前に保存する
            result.save(output)
        }
    }
}
