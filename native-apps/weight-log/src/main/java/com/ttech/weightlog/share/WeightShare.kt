package com.ttech.weightlog.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.ttech.common.share.shareTextFile
import com.ttech.weightlog.domain.Profile
import com.ttech.weightlog.domain.Stats
import com.ttech.weightlog.domain.WeightEntry
import com.ttech.weightlog.domain.WeightFile
import com.ttech.weightlog.domain.entriesText
import com.ttech.weightlog.domain.weightSummaryText

fun shareText(context: Context, subject: String, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

fun shareSummary(context: Context, stats: Stats, profile: Profile) = shareText(context, "体重の記録", weightSummaryText(stats, profile), "記録を共有")

fun shareEntries(context: Context, entries: List<WeightEntry>) = shareText(context, "体重の記録一覧", entriesText(entries), "一覧を共有")

/** バックアップを共有する。機種変更や、ほかの端末への引き継ぎに使う */
fun shareBackup(context: Context, entries: List<WeightEntry>, profile: Profile) {
    shareTextFile(
        context = context,
        content = WeightFile.encode(entries, profile),
        filename = WeightFile.fileName(),
        mimeType = "application/octet-stream",
        chooserTitle = "バックアップを共有",
    )
}
