package com.ttech.ideamemo.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.ttech.common.share.shareTextFile
import com.ttech.ideamemo.domain.Idea
import com.ttech.ideamemo.domain.IdeaFile
import com.ttech.ideamemo.domain.ideasText
import com.ttech.ideamemo.domain.toShareText

fun shareText(context: Context, subject: String, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

fun shareIdea(context: Context, idea: Idea) = shareText(context, "アプリのアイデア: ${idea.title}", idea.toShareText(), "アイデアを共有")

fun shareIdeas(context: Context, ideas: List<Idea>) = shareText(context, "アプリのアイデア帳", ideasText(ideas), "リストを共有")

/** バックアップを共有する。機種変更や、ほかの端末への引き継ぎに使う */
fun shareBackup(context: Context, ideas: List<Idea>) {
    shareTextFile(
        context = context,
        content = IdeaFile.encode(ideas),
        filename = IdeaFile.fileName(),
        mimeType = "application/octet-stream",
        chooserTitle = "バックアップを共有",
    )
}
