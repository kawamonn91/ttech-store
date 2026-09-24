package com.ttech.pdftoolkit.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** PDFの結合・ページ抽出(Apache PDFBoxのAndroid版)。時間がかかるので、呼び出し側でIOスレッドから使うこと。 */
object PdfOps {
    fun displayName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "PDF"
    }

    fun pageCount(context: Context, uri: Uri): Int =
        open(context, uri).use { input -> PDDocument.load(input).use { it.numberOfPages } }

    /** [uris] の順にページをつなげて [output] に書き出す。 */
    fun merge(context: Context, uris: List<Uri>, output: File) {
        val inputs = uris.map { open(context, it) }
        try {
            val merger = PDFMergerUtility()
            inputs.forEach { merger.addSource(it) }
            FileOutputStream(output).use { out ->
                merger.destinationStream = out
                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            }
        } finally {
            inputs.forEach { runCatching { it.close() } }
        }
    }

    /** [indices](0始まり)のページだけを、その順に [output] へ書き出す。 */
    fun extract(context: Context, uri: Uri, indices: List<Int>, output: File) {
        open(context, uri).use { input ->
            PDDocument.load(input).use { source ->
                PDDocument().use { result ->
                    indices.forEach { result.importPage(source.getPage(it)) }
                    result.save(output)
                }
            }
        }
    }

    private fun open(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("ファイルを開けませんでした")
}
