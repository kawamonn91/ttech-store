package com.ttech.babylog.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.babylog.domain.BabyEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("baby_log")
private val EVENTS_KEY = stringPreferencesKey("events")
private val json = Json { ignoreUnknownKeys = true }

/** 授乳・オムツ替えの記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class BabyEventStore(private val context: Context) {
    val events: Flow<List<BabyEvent>> = context.dataStore.data.map { prefs -> decode(prefs[EVENTS_KEY]) }

    suspend fun add(event: BabyEvent) {
        context.dataStore.edit { prefs ->
            prefs[EVENTS_KEY] = json.encodeToString(listOf(event) + decode(prefs[EVENTS_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[EVENTS_KEY] = json.encodeToString(decode(prefs[EVENTS_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<BabyEvent> =
        raw?.let { runCatching { json.decodeFromString<List<BabyEvent>>(it) }.getOrNull() } ?: emptyList()
}
