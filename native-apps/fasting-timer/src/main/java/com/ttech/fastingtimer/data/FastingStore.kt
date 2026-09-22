package com.ttech.fastingtimer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.fastingtimer.domain.FastSession
import com.ttech.fastingtimer.domain.pushHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("fasting_timer")
private val SESSION_KEY = stringPreferencesKey("session")
private val HISTORY_KEY = stringPreferencesKey("history_hours")
private val json = Json { ignoreUnknownKeys = true }

/**
 * 断食セッションと記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。
 * セッションを永続化することで、アプリを再起動しても実際の経過時間から再開できる。
 */
class FastingStore(private val context: Context) {
    val session: Flow<FastSession?> = context.dataStore.data.map { prefs ->
        prefs[SESSION_KEY]?.let { runCatching { json.decodeFromString<FastSession>(it) }.getOrNull() }
    }
    val history: Flow<List<Double>> = context.dataStore.data.map { decodeHistory(it[HISTORY_KEY]) }

    suspend fun start(session: FastSession) {
        context.dataStore.edit { it[SESSION_KEY] = json.encodeToString(session) }
    }

    suspend fun stopAndRecord(hours: Double?) {
        context.dataStore.edit { prefs ->
            prefs.remove(SESSION_KEY)
            if (hours != null) {
                val updated = decodeHistory(prefs[HISTORY_KEY])
                prefs[HISTORY_KEY] = json.encodeToString(updated.pushHistory(hours))
            }
        }
    }

    private fun decodeHistory(raw: String?): List<Double> =
        raw?.let { runCatching { json.decodeFromString<List<Double>>(it) }.getOrNull() } ?: emptyList()
}
