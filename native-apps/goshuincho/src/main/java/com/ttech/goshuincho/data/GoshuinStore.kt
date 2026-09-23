package com.ttech.goshuincho.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.goshuincho.domain.GoshuinEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("goshuincho")
private val ENTRIES_KEY = stringPreferencesKey("entries")
private val json = Json { ignoreUnknownKeys = true }

/** 御朱印の記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class GoshuinStore(private val context: Context) {
    val entries: Flow<List<GoshuinEntry>> = context.dataStore.data.map { decode(it[ENTRIES_KEY]) }

    suspend fun add(entry: GoshuinEntry) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(listOf(entry) + decode(prefs[ENTRIES_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(decode(prefs[ENTRIES_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<GoshuinEntry> =
        raw?.let { runCatching { json.decodeFromString<List<GoshuinEntry>>(it) }.getOrNull() } ?: emptyList()
}
