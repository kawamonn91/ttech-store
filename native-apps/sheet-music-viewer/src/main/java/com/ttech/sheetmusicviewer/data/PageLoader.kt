package com.ttech.sheetmusicviewer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.ttech.sheetmusicviewer.domain.calcSampleSize
import com.ttech.sheetmusicviewer.domain.exifRotationDegrees

/**
 * 楽譜の写真を、画面表示に十分な大きさに縮小して読み込む(スマホで撮った写真は数千pxあり、
 * そのまま読むとメモリを使いすぎる)。カメラで撮った写真の向き(EXIF)も反映する。
 * 時間がかかるので、呼び出し側でIOスレッドから使うこと。読み込めなければ null。
 */
fun loadPageBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calcSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

    val orientation = runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    val degrees = exifRotationDegrees(orientation)
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
