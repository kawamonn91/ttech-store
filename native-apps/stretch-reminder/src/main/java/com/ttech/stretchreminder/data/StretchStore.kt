package com.ttech.stretchreminder.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.stretchreminder.domain.DEFAULT_INTERVAL_MIN
import com.ttech.stretchreminder.domain.DailyCount
import com.ttech.stretchreminder.domain.increment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("stretch_reminder")
private val INTERVAL_KEY = intPreferencesKey("interval_min")
private val LAST_DONE_KEY = longPreferencesKey("last_done_at")
private val COUNT_KEY = stringPreferencesKey("completed_today")
private val json = Json { ignoreUnknownKeys = true }

data class StretchState(val intervalMin: Int, val lastDoneAt: Long, val completedToday: DailyCount?)

/** 間隔・前回の実施時刻・今日の実施回数を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class StretchStore(private val context: Context) {
    /** 初回起動時は前回の実施時刻が無いので、読み込んだ時刻から数え始める(Webアプリ版と同じ)。 */
    val state: Flow<StretchState> = context.dataStore.data.map { prefs ->
        StretchState(
            intervalMin = prefs[INTERVAL_KEY] ?: DEFAULT_INTERVAL_MIN,
            lastDoneAt = prefs[LAST_DONE_KEY] ?: System.currentTimeMillis(),
            completedToday = prefs[COUNT_KEY]?.let { runCatching { json.decodeFromString<DailyCount>(it) }.getOrNull() },
        )
    }

    /** 前回の実施時刻が未保存なら今を保存する(通知の予約基準を固定するため)。 */
    suspend fun initLastDoneIfMissing(now: Long) {
        context.dataStore.edit { if (it[LAST_DONE_KEY] == null) it[LAST_DONE_KEY] = now }
    }

    suspend fun setInterval(minutes: Int) {
        context.dataStore.edit { it[INTERVAL_KEY] = minutes }
    }

    suspend fun markDone(now: Long, today: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[COUNT_KEY]?.let { runCatching { json.decodeFromString<DailyCount>(it) }.getOrNull() }
            prefs[LAST_DONE_KEY] = now
            prefs[COUNT_KEY] = json.encodeToString(current.increment(today))
        }
    }
}
