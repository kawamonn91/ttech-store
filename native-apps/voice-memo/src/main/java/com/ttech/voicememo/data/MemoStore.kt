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

    suspend fun add(memo: Memo) {
        context.dataStore.edit { prefs ->
            prefs[MEMOS_KEY] = json.encodeToString(listOf(memo) + decode(prefs[MEMOS_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[MEMOS_KEY] = json.encodeToString(decode(prefs[MEMOS_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<Memo> =
        raw?.let { runCatching { json.decodeFromString<List<Memo>>(it) }.getOrNull() } ?: emptyList()
}
