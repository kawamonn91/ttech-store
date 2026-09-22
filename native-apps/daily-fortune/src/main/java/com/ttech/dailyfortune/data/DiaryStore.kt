package com.ttech.dailyfortune.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.dailyfortune.domain.DiaryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("daily_fortune")
private val ENTRIES_KEY = stringPreferencesKey("entries")
private val json = Json { ignoreUnknownKeys = true }

/** 一言日記を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class DiaryStore(private val context: Context) {
    val entries: Flow<List<DiaryEntry>> = context.dataStore.data.map { decode(it[ENTRIES_KEY]) }

    suspend fun add(entry: DiaryEntry) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(listOf(entry) + decode(prefs[ENTRIES_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(decode(prefs[ENTRIES_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<DiaryEntry> =
        raw?.let { runCatching { json.decodeFromString<List<DiaryEntry>>(it) }.getOrNull() } ?: emptyList()
}
