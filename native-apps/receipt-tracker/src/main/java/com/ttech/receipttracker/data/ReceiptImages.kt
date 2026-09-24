package com.ttech.receipttracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** 保存時の長辺の上限(px)。レシートの文字が読める程度に縮小して容量を抑える。 */
private const val MAX_EDGE = 1600

private fun imageDir(context: Context): File = File(context.filesDir, "receipts").apply { mkdirs() }

/** 選んだ画像を縮小してアプリ専用領域にJPEGで保存し、ファイル名を返す。読み込めなければnull。 */
fun saveReceiptImage(context: Context, uri: Uri, id: String): String? {
    val source = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: return null
    val scale = MAX_EDGE.toFloat() / max(source.width, source.height)
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt(), (source.height * scale).roundToInt(), true)
    } else {
        source
    }
    val name = "$id.jpg"
    File(imageDir(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    return name
}

/** 一覧のサムネイル用に縮小して読み込む。 */
fun loadReceiptThumbnail(context: Context, name: String, sampleSize: Int = 8): Bitmap? {
    val file = File(imageDir(context), name)
    if (!file.exists()) return null
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}

fun deleteReceiptImage(context: Context, name: String) {
    File(imageDir(context), name).delete()
}
