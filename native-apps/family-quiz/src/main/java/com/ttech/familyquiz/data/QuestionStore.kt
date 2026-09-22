package com.ttech.familyquiz.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.familyquiz.domain.Question
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("family_quiz")
private val QUESTIONS_KEY = stringPreferencesKey("questions")
private val json = Json { ignoreUnknownKeys = true }

/** 作成したクイズを端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class QuestionStore(private val context: Context) {
    val questions: Flow<List<Question>> = context.dataStore.data.map { decode(it[QUESTIONS_KEY]) }

    /** Webアプリ版と同じく、末尾に追加する(新しい問題ほど後の出題順になる)。 */
    suspend fun add(question: Question) {
        context.dataStore.edit { prefs ->
            prefs[QUESTIONS_KEY] = json.encodeToString(decode(prefs[QUESTIONS_KEY]) + question)
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[QUESTIONS_KEY] = json.encodeToString(decode(prefs[QUESTIONS_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<Question> =
        raw?.let { runCatching { json.decodeFromString<List<Question>>(it) }.getOrNull() } ?: emptyList()
}
