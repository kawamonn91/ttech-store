package com.ttech.watertracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.watertracker.domain.DEFAULT_GOAL_ML
import com.ttech.watertracker.domain.DailyLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("water_tracker")
private val GOAL_KEY = intPreferencesKey("goal_ml")
private val LOGS_KEY = stringPreferencesKey("logs")
private val json = Json { ignoreUnknownKeys = true }

/** 1日の目標量と日ごとの摂取量を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class WaterStore(private val context: Context) {
    val goalMl: Flow<Int> = context.dataStore.data.map { it[GOAL_KEY] ?: DEFAULT_GOAL_ML }
    val logs: Flow<List<DailyLog>> = context.dataStore.data.map { decode(it[LOGS_KEY]) }

    suspend fun setGoal(ml: Int) {
        context.dataStore.edit { it[GOAL_KEY] = ml }
    }

    suspend fun updateLogs(transform: (List<DailyLog>) -> List<DailyLog>) {
        context.dataStore.edit { prefs ->
            prefs[LOGS_KEY] = json.encodeToString(transform(decode(prefs[LOGS_KEY])))
        }
    }

    private fun decode(raw: String?): List<DailyLog> =
        raw?.let { runCatching { json.decodeFromString<List<DailyLog>>(it) }.getOrNull() } ?: emptyList()
}
