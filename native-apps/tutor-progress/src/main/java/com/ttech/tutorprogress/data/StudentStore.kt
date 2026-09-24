package com.ttech.tutorprogress.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.tutorprogress.domain.Student
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("tutor_progress")
private val STUDENTS_KEY = stringPreferencesKey("students")
private val json = Json { ignoreUnknownKeys = true }

/** 生徒と進捗記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class StudentStore(private val context: Context) {
    val students: Flow<List<Student>> = context.dataStore.data.map { decode(it[STUDENTS_KEY]) }

    suspend fun add(item: Student) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Student>) -> List<Student>) {
        context.dataStore.edit { prefs ->
            prefs[STUDENTS_KEY] = json.encodeToString(transform(decode(prefs[STUDENTS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Student> =
        raw?.let { runCatching { json.decodeFromString<List<Student>>(it) }.getOrNull() } ?: emptyList()
}
