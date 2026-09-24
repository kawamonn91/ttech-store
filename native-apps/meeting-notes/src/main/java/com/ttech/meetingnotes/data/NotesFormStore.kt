package com.ttech.meetingnotes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.meetingnotes.domain.NotesForm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("meeting_notes")
private val FORM_KEY = stringPreferencesKey("form")
private val json = Json { ignoreUnknownKeys = true }

/** 入力途中のフォームを端末内に残す(Webアプリ版の localStorage 保存に相当)。未保存ならnull。 */
class NotesFormStore(private val context: Context) {
    val form: Flow<NotesForm?> = context.dataStore.data.map { prefs ->
        prefs[FORM_KEY]?.let { raw -> runCatching { json.decodeFromString<NotesForm>(raw) }.getOrNull() }
    }

    suspend fun save(form: NotesForm) {
        context.dataStore.edit { it[FORM_KEY] = json.encodeToString(form) }
    }
}
