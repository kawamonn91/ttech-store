package com.ttech.pdftoolkit.data

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** :common のFileProvider(キャッシュ内の shared/ 配下)にあるPDFを、共有シートで他のアプリへ渡す・保存する。 */
fun sharePdf(context: Context, file: File, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "pdf", uri)
    }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

/** 共有用の書き出し先。ファイル名は固定なので、前回の結果は上書きされる。 */
fun sharedOutputFile(context: Context, name: String): File =
    File(context.cacheDir, "shared").apply { mkdirs() }.let { File(it, name) }
