package com.ttech.imagebatch.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.ttech.imagebatch.domain.computeDrawRect

/**
 * 選んだ画像を、指定した背景色・サイズのキャンバス中央に配置したBitmapを作る
 * (Webアプリ版のcanvas 2D描画と同じ結果になる)。
 */
fun processImage(context: Context, uri: Uri, backgroundColor: Int, targetWidth: Int, targetHeight: Int): Bitmap {
    val source = context.contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(input)
    } ?: error("画像の読み込みに失敗しました")

    val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(backgroundColor)

    val rect = computeDrawRect(source.width, source.height, targetWidth, targetHeight)
    val destRect = android.graphics.RectF(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
    canvas.drawBitmap(source, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

    return result
}
