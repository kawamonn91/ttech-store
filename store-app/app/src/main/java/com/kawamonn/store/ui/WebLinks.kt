package com.kawamonn.store.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.kawamonn.store.BuildConfig

/** ストアのWebページ(開発者ページ・規約など)を、端末のブラウザで開く。[path] は "/developer" のような先頭が / のパス */
fun openStoreWebPage(context: Context, path: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.STORE_WEB_BASE + path)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "ブラウザが見つかりません", Toast.LENGTH_SHORT).show()
    }
}
