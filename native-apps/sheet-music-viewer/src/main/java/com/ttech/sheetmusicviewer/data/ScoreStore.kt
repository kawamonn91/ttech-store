package com.ttech.sheetmusicviewer.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.sheetmusicviewer.domain.Score
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("sheet_music_viewer")
private val SCORE_KEY = stringPreferencesKey("score")
private val json = Json { ignoreUnknownKeys = true }

/**
 * 読み込んだ楽譜(画像URIの並び)と表示中のページを端末内に残し、次回起動時も続きから開けるようにする
 * (Webアプリ版は保存せず、開き直すと読み込み直しだった)。
 */
class ScoreStore(private val context: Context) {
    val score: Flow<Score> = context.dataStore.data.map { prefs ->
        prefs[SCORE_KEY]?.let { runCatching { json.decodeFromString<Score>(it) }.getOrNull() } ?: Score()
    }

    /** 新しい楽譜を読み込む。前の楽譜の読み取り権限は手放す。 */
    suspend fun load(uris: List<Uri>) {
        context.dataStore.edit { prefs ->
            val previous = prefs[SCORE_KEY]?.let { runCatching { json.decodeFromString<Score>(it) }.getOrNull() }
            previous?.pages?.forEach { releasePermission(Uri.parse(it)) }
            uris.forEach { takePermission(it) }
            prefs[SCORE_KEY] = json.encodeToString(Score(pages = uris.map(Uri::toString)))
        }
    }

    suspend fun save(score: Score) {
        context.dataStore.edit { it[SCORE_KEY] = json.encodeToString(score) }
    }

    suspend fun clear() = load(emptyList())

    // フォトピッカーのURIは、永続的な読み取り権限を取っておけばアプリを再起動しても読める
    private fun takePermission(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun releasePermission(uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

/** 画面に表示する大きさ(長辺 [maxEdge] px 程度)まで縮小して読み込む。読めなければnull。 */
fun loadPageBitmap(context: Context, uri: Uri, maxEdge: Int = 2400): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    context.contentResolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()
