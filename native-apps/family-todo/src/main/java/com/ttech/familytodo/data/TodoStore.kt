package com.ttech.familytodo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.familytodo.domain.TodoItem
import com.ttech.familytodo.domain.withoutDone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("family_todo")
private val ITEMS_KEY = stringPreferencesKey("items")
private val json = Json { ignoreUnknownKeys = true }

/** ToDo・買い物リストの項目を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class TodoStore(private val context: Context) {
    val items: Flow<List<TodoItem>> = context.dataStore.data.map { decode(it[ITEMS_KEY]) }

    suspend fun add(item: TodoItem) {
        context.dataStore.edit { prefs ->
            prefs[ITEMS_KEY] = json.encodeToString(decode(prefs[ITEMS_KEY]) + item)
        }
    }

    suspend fun toggle(id: String) {
        context.dataStore.edit { prefs ->
            val updated = decode(prefs[ITEMS_KEY]).map { if (it.id == id) it.copy(done = !it.done) else it }
            prefs[ITEMS_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[ITEMS_KEY] = json.encodeToString(decode(prefs[ITEMS_KEY]).filter { it.id != id })
        }
    }

    suspend fun clearDone() {
        context.dataStore.edit { prefs ->
            prefs[ITEMS_KEY] = json.encodeToString(decode(prefs[ITEMS_KEY]).withoutDone())
        }
    }

    private fun decode(raw: String?): List<TodoItem> =
        raw?.let { runCatching { json.decodeFromString<List<TodoItem>>(it) }.getOrNull() } ?: emptyList()
}
