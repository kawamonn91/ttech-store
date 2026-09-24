package com.ttech.voicememo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.voicememo.domain.Memo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("voice_memo")
private val MEMOS_KEY = stringPreferencesKey("memos")
private val json = Json { ignoreUnknownKeys = true }

/** 文字起こししたメモを端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class MemoStore(private val context: Context) {
    val memos: Flow<List<Memo>> = context.dataStore.data.map { decode(it[MEMOS_KEY]) }

    suspend fun add(item: Memo) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Memo>) -> List<Memo>) {
        context.dataStore.edit { prefs ->
            prefs[MEMOS_KEY] = json.encodeToString(transform(decode(prefs[MEMOS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Memo> =
        raw?.let { runCatching { json.decodeFromString<List<Memo>>(it) }.getOrNull() } ?: emptyList()
}
