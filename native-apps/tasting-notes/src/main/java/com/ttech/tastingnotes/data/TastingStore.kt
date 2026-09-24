package com.ttech.tastingnotes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.tastingnotes.domain.Tasting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("tasting_notes")
private val ITEMS_KEY = stringPreferencesKey("items")
private val json = Json { ignoreUnknownKeys = true }

/** テイスティングメモを端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class TastingStore(private val context: Context) {
    val items: Flow<List<Tasting>> = context.dataStore.data.map { decode(it[ITEMS_KEY]) }

    suspend fun add(item: Tasting) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Tasting>) -> List<Tasting>) {
        context.dataStore.edit { prefs ->
            prefs[ITEMS_KEY] = json.encodeToString(transform(decode(prefs[ITEMS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Tasting> =
        raw?.let { runCatching { json.decodeFromString<List<Tasting>>(it) }.getOrNull() } ?: emptyList()
}
