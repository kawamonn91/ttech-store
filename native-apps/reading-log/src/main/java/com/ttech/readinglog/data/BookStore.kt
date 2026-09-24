package com.ttech.readinglog.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.readinglog.domain.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("reading_log")
private val BOOKS_KEY = stringPreferencesKey("books")
private val json = Json { ignoreUnknownKeys = true }

/** 読了した本の記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class BookStore(private val context: Context) {
    val books: Flow<List<Book>> = context.dataStore.data.map { decode(it[BOOKS_KEY]) }

    suspend fun add(item: Book) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Book>) -> List<Book>) {
        context.dataStore.edit { prefs ->
            prefs[BOOKS_KEY] = json.encodeToString(transform(decode(prefs[BOOKS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Book> =
        raw?.let { runCatching { json.decodeFromString<List<Book>>(it) }.getOrNull() } ?: emptyList()
}
