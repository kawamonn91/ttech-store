package com.ttech.workoutlog.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.workoutlog.domain.WorkoutEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("workout_log")
private val ENTRIES_KEY = stringPreferencesKey("entries")
private val json = Json { ignoreUnknownKeys = true }

/** 筋トレ記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class WorkoutStore(private val context: Context) {
    val entries: Flow<List<WorkoutEntry>> = context.dataStore.data.map { decode(it[ENTRIES_KEY]) }

    suspend fun add(item: WorkoutEntry) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<WorkoutEntry>) -> List<WorkoutEntry>) {
        context.dataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = json.encodeToString(transform(decode(prefs[ENTRIES_KEY])))
        }
    }

    private fun decode(raw: String?): List<WorkoutEntry> =
        raw?.let { runCatching { json.decodeFromString<List<WorkoutEntry>>(it) }.getOrNull() } ?: emptyList()
}
