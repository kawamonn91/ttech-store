package com.ttech.fishinglog.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.fishinglog.domain.Catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("fishing_log")
private val CATCHES_KEY = stringPreferencesKey("catches")
private val json = Json { ignoreUnknownKeys = true }

/** 釣果記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class CatchStore(private val context: Context) {
    val catches: Flow<List<Catch>> = context.dataStore.data.map { decode(it[CATCHES_KEY]) }

    suspend fun add(item: Catch) {
        context.dataStore.edit { prefs ->
            prefs[CATCHES_KEY] = json.encodeToString(listOf(item) + decode(prefs[CATCHES_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[CATCHES_KEY] = json.encodeToString(decode(prefs[CATCHES_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<Catch> =
        raw?.let { runCatching { json.decodeFromString<List<Catch>>(it) }.getOrNull() } ?: emptyList()
}
