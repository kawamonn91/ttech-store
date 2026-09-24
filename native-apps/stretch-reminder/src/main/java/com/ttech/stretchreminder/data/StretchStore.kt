package com.ttech.stretchreminder.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.stretchreminder.domain.DEFAULT_INTERVAL_MIN
import com.ttech.stretchreminder.domain.StretchState
import com.ttech.stretchreminder.domain.markDone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("stretch_reminder")
private val INTERVAL_KEY = intPreferencesKey("interval_min")
private val LAST_DONE_KEY = longPreferencesKey("last_done_at")
private val DATE_KEY = stringPreferencesKey("completed_date")
private val COUNT_KEY = intPreferencesKey("completed_count")

/** 初回起動時は「今この瞬間に完了した」ものとして数え始める(Web版の `Date.now()` 初期値と同じ)。 */
private val processStartedAt = System.currentTimeMillis()

fun defaultStretchState() = StretchState(DEFAULT_INTERVAL_MIN, processStartedAt, "", 0)

/** リマインド間隔・最後の完了時刻・今日の実施回数を端末内に残す(Web版の localStorage 保存に相当)。 */
class StretchStore(private val context: Context) {
    val state: Flow<StretchState> = context.dataStore.data.map { prefs ->
        StretchState(
            intervalMin = prefs[INTERVAL_KEY] ?: DEFAULT_INTERVAL_MIN,
            lastDoneAt = prefs[LAST_DONE_KEY] ?: processStartedAt,
            completedDate = prefs[DATE_KEY] ?: "",
            completedCount = prefs[COUNT_KEY] ?: 0,
        )
    }

    suspend fun current(): StretchState = state.first()

    /** 初回起動時に「最後の完了時刻」を確定させて保存する。 */
    suspend fun ensureInitialized() {
        context.dataStore.edit { prefs ->
            if (prefs[LAST_DONE_KEY] == null) prefs[LAST_DONE_KEY] = processStartedAt
        }
    }

    suspend fun setInterval(intervalMin: Int) {
        context.dataStore.edit { prefs -> prefs[INTERVAL_KEY] = intervalMin }
    }

    suspend fun markDone(now: Long, today: String) {
        context.dataStore.edit { prefs ->
            val current = StretchState(
                intervalMin = prefs[INTERVAL_KEY] ?: DEFAULT_INTERVAL_MIN,
                lastDoneAt = prefs[LAST_DONE_KEY] ?: processStartedAt,
                completedDate = prefs[DATE_KEY] ?: "",
                completedCount = prefs[COUNT_KEY] ?: 0,
            )
            val next = markDone(current, now, today)
            prefs[LAST_DONE_KEY] = next.lastDoneAt
            prefs[DATE_KEY] = next.completedDate
            prefs[COUNT_KEY] = next.completedCount
        }
    }
}
