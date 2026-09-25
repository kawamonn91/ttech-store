package com.ttech.travelwishlist.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ttech.common.share.shareTextFile
import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.WishFile
import com.ttech.travelwishlist.domain.toShareText
import com.ttech.travelwishlist.domain.wishlistText

fun shareText(context: Context, subject: String, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

fun sharePlace(context: Context, place: Place) = shareText(context, "行きたい場所: ${place.name}", place.toShareText(), "行きたい場所を共有")

fun shareWishlist(context: Context, places: List<Place>) = shareText(context, "行きたい旅メモ", wishlistText(places), "リストを共有")

/** バックアップ(メモファイル)を共有する。機種変更や、家族のアプリへの引き継ぎに使う */
fun shareBackup(context: Context, places: List<Place>) {
    shareTextFile(
        context = context,
        content = WishFile.encode(places),
        filename = WishFile.fileName(),
        mimeType = "application/octet-stream",
        chooserTitle = "バックアップを共有",
    )
}

/** 地図アプリで場所を開く(端末の地図アプリに渡すだけで、このアプリは通信しない) */
fun openInMaps(context: Context, query: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "地図アプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

/** 参考URLをブラウザで開く。http・https だけ(保存時にも確認しているが、開くときにも確認する) */
fun openUrl(context: Context, url: String) {
    if (!(url.startsWith("https://") || url.startsWith("http://"))) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "ブラウザが見つかりません", Toast.LENGTH_SHORT).show()
    }
}
