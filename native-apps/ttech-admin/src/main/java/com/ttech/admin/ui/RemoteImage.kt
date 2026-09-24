package com.ttech.admin.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 短時間だけ有効な署名付きURLの画像を、読み込んで表示する(管理者が日記の写真を確認するため)。
 * 画像ライブラリを増やさず、通信済みの OkHttp で取得する。大きい画像は縮小して読む。
 */
@Composable
fun RemoteImage(url: String, client: OkHttpClient, modifier: Modifier = Modifier) {
    val state by produceState<Result<Bitmap>?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "画像を読み込めませんでした (${response.code})" }
                    val bytes = response.body.bytes()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxSide = 1280)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: error("画像を表示できませんでした")
                }
            }
        }
    }
    val result = state
    when {
        result == null -> Text("写真を読み込み中…")
        result.isFailure -> Text(result.exceptionOrNull()?.message ?: "画像を表示できませんでした")
        else -> Image(
            bitmap = result.getOrThrow().asImageBitmap(),
            contentDescription = "添付の写真",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxWidth().heightIn(max = 320.dp),
        )
    }
}

/** 縮小しても長辺が maxSide 以上に保たれる、最大の2のべき乗の縮小率(表示に十分な解像度を残しつつ、メモリを節約する) */
internal fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= maxSide) {
        sample *= 2
        longest /= 2
    }
    return sample
}
