package com.ttech.pdftoolkit.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ttech.pdftoolkit.domain.extractPages
import com.ttech.pdftoolkit.domain.mergePdfs
import com.ttech.pdftoolkit.domain.pageCount
import java.io.IOException

/** 端末上のPDF(content:// URI)を読み書きする。重い処理なので呼び出し側で IO スレッドから呼ぶ。 */
class PdfFiles(private val context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    fun pageCount(uri: Uri): Int = open(uri).use { pageCount(it) }

    fun merge(sources: List<Uri>, destination: Uri) {
        val inputs = sources.map(::open)
        try {
            output(destination).use { mergePdfs(inputs, it) }
        } finally {
            inputs.forEach { runCatching { it.close() } }
        }
    }

    fun extract(source: Uri, pageIndices: List<Int>, destination: Uri) {
        open(source).use { input -> output(destination).use { extractPages(input, pageIndices, it) } }
    }

    private fun open(uri: Uri) = context.contentResolver.openInputStream(uri) ?: throw IOException("ファイルを開けませんでした")

    private fun output(uri: Uri) = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("保存先に書き込めませんでした")
}

/**
 * 処理に失敗したときに画面へ出すメッセージ。暗号化(パスワード付き)PDFは、暗号処理のライブラリを
 * 同梱していないため読み込めない(クラスが見つからないエラーになる)。
 */
fun failureMessage(error: Throwable, fallback: String): String = when {
    error is NoClassDefFoundError || error.message?.contains("encrypt", ignoreCase = true) == true ->
        "パスワード付き(暗号化された)PDFには対応していません"
    error is IOException && error.message != null -> "$fallback(${error.message})"
    else -> fallback
}
