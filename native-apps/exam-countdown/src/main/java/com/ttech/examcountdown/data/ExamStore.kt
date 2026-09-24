package com.ttech.examcountdown.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.examcountdown.domain.StudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore("exam_countdown")
private val EXAM_NAME_KEY = stringPreferencesKey("exam_name")
private val EXAM_DATE_KEY = stringPreferencesKey("exam_date")
private val SESSIONS_KEY = stringPreferencesKey("sessions")
private val json = Json { ignoreUnknownKeys = true }

/** 試験情報と学習記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class ExamStore(private val context: Context) {
    val examName: Flow<String> = context.dataStore.data.map { it[EXAM_NAME_KEY] ?: "" }
    val examDate: Flow<String> = context.dataStore.data.map { it[EXAM_DATE_KEY] ?: LocalDate.now().toString() }
    val sessions: Flow<List<StudySession>> = context.dataStore.data.map { decode(it[SESSIONS_KEY]) }

    suspend fun setExamName(name: String) {
        context.dataStore.edit { it[EXAM_NAME_KEY] = name }
    }

    suspend fun setExamDate(date: String) {
        context.dataStore.edit { it[EXAM_DATE_KEY] = date }
    }

    suspend fun addSession(session: StudySession) {
        context.dataStore.edit { prefs ->
            prefs[SESSIONS_KEY] = json.encodeToString(listOf(session) + decode(prefs[SESSIONS_KEY]))
        }
    }

    private fun decode(raw: String?): List<StudySession> =
        raw?.let { runCatching { json.decodeFromString<List<StudySession>>(it) }.getOrNull() } ?: emptyList()
}
