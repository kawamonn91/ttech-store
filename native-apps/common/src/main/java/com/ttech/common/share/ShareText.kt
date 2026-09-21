package com.ttech.common.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * テキストファイル(vCard・CSVなど)をキャッシュに書き出し、共有シートを開く。
 * FileProvider経由のcontent:// URIにする理由・ClipDataを載せる理由は [shareBitmap] と同じ。
 */
fun shareTextFile(
    context: Context,
    content: String,
    filename: String,
    mimeType: String = "text/plain",
    chooserTitle: String? = null,
) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, filename)
    file.writeText(content)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "file", uri)
    }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { context !is android.app.Activity } ?: 0)
    }
    context.startActivity(chooser)
}
