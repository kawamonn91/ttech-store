package com.ttech.tripshiori.share

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ttech.common.share.shareTextFile
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.TripFile
import com.ttech.tripshiori.domain.toShareText
import java.io.File

/** しおりを、LINE・メールなどに貼り付けられるテキストとして共有する */
fun shareTripText(context: Context, trip: Trip) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "旅のしおり: ${trip.title}")
        putExtra(Intent.EXTRA_TEXT, trip.toShareText())
    }
    context.startActivity(chooser(context, intent, "しおりを共有"))
}

fun copyTripText(context: Context, trip: Trip) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("旅のしおり", trip.toShareText()))
}

/** 見た目を整えたPDFにして共有する */
fun shareTripPdf(context: Context, trip: Trip) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, TripFile.fileName(trip).removeSuffix(".${TripFile.EXTENSION}") + ".pdf")
    file.outputStream().use { TripPdf.write(trip, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "旅のしおり: ${trip.title}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "PDF", uri)
    }
    context.startActivity(chooser(context, intent, "PDFを共有").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** 「しおりファイル」として共有する。相手の「旅のしおり」アプリで読み込める */
fun shareTripFile(context: Context, trip: Trip) {
    shareTextFile(
        context = context,
        content = TripFile.encode(trip),
        filename = TripFile.fileName(trip),
        mimeType = "application/octet-stream",
        chooserTitle = "しおりファイルを共有",
    )
}

private fun chooser(context: Context, intent: Intent, title: String): Intent =
    Intent.createChooser(intent, title).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
