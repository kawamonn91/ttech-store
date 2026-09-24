package com.ttech.cycletracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.cycletracker.domain.CycleEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("cycle_tracker")
private val ENTRIES_KEY = stringPreferencesKey("entries")
private val CYCLE_LENGTH_KEY = intPreferencesKey("cycle_length_days")
private val PERIOD_LENGTH_KEY = intPreferencesKey("period_length_days")
private val json = Json { ignoreUnknownKeys = true }

/** 記録と設定を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class CycleStore(private val context: Context) {
    val entries: Flow<List<CycleEntry>> = context.dataStore.data.map { decode(it[ENTRIES_KEY]) }
    val cycleLengthDays: Flow<Int> = context.dataStore.data.map { it[CYCLE_LENGTH_KEY] ?: 28 }
    val periodLengthDays: Flow<Int> = context.dataStore.data.map { it[PERIOD_LENGTH_KEY] ?: 5 }

    suspend fun add(entry: CycleEntry) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(listOf(entry) + decode(prefs[ENTRIES_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(decode(prefs[ENTRIES_KEY]).filter { it.id != id })
        }
    }

    suspend fun setCycleLengthDays(days: Int) {
        context.dataStore.edit { it[CYCLE_LENGTH_KEY] = days }
    }

    suspend fun setPeriodLengthDays(days: Int) {
        context.dataStore.edit { it[PERIOD_LENGTH_KEY] = days }
    }

    private fun decode(raw: String?): List<CycleEntry> =
        raw?.let { runCatching { json.decodeFromString<List<CycleEntry>>(it) }.getOrNull() } ?: emptyList()
}
