package com.ttech.common.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * PNG画像をキャッシュに書き出し、共有シート(他のアプリへの共有・保存)を開く。
 *
 * Android 7以降は file:// のURIを他アプリに渡すと例外になるため、FileProvider経由の
 * content:// URIにする必要がある。この仕組みは :common モジュールの
 * AndroidManifest.xml に一度だけ定義してあるので、呼び出す側(各アプリ)は
 * 何も設定せずにこの関数を呼ぶだけでよい。
 */
fun shareBitmap(context: Context, bitmap: Bitmap, filename: String = "image.png", chooserTitle: String? = null) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, filename)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ACTION_SEND + FLAG_GRANT_READ_URI_PERMISSION だけだと、ユーザーが選ぶ前の
        // チューザー画面(プレビュー表示)自体には権限が渡らずSecurityExceptionになる。
        // ClipDataとして同じUriを載せることで、チューザー自身にも読み取り権限が伝わる。
        clipData = ClipData.newUri(context.contentResolver, "image", uri)
    }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { context !is android.app.Activity } ?: 0)
    }
    context.startActivity(chooser)
}
