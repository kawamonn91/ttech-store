package com.ttech.childgrowth.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.childgrowth.domain.GrowthEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("child_growth")
private val NAME_KEY = stringPreferencesKey("child_name")
private val ENTRIES_KEY = stringPreferencesKey("entries")
private val json = Json { ignoreUnknownKeys = true }

/** 子供の名前と成長記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class ChildGrowthStore(private val context: Context) {
    val childName: Flow<String> = context.dataStore.data.map { it[NAME_KEY] ?: "" }
    val entries: Flow<List<GrowthEntry>> = context.dataStore.data.map { decode(it[ENTRIES_KEY]) }

    suspend fun setChildName(name: String) {
        context.dataStore.edit { it[NAME_KEY] = name }
    }

    suspend fun add(entry: GrowthEntry) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(listOf(entry) + decode(prefs[ENTRIES_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(decode(prefs[ENTRIES_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<GrowthEntry> =
        raw?.let { runCatching { json.decodeFromString<List<GrowthEntry>>(it) }.getOrNull() } ?: emptyList()
}
