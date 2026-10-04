package com.ttech.attendancecount.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.attendancecount.domain.EventItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("attendance_count")
private val EVENTS_KEY = stringPreferencesKey("events")
private val json = Json { ignoreUnknownKeys = true }

/** イベントの一覧を端末内に保存する */
class EventStore(private val context: Context) {
    val events: Flow<List<EventItem>> = context.dataStore.data.map { prefs ->
        prefs[EVENTS_KEY]?.let { raw ->
            runCatching { json.decodeFromString<List<EventItem>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun save(events: List<EventItem>) {
        context.dataStore.edit { it[EVENTS_KEY] = json.encodeToString(events) }
    }
}
