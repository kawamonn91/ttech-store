package com.ttech.movielog.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.movielog.domain.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("movie_log")
private val MOVIES_KEY = stringPreferencesKey("movies")
private val json = Json { ignoreUnknownKeys = true }

/** 視聴記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class MovieStore(private val context: Context) {
    val movies: Flow<List<Movie>> = context.dataStore.data.map { decode(it[MOVIES_KEY]) }

    suspend fun add(item: Movie) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Movie>) -> List<Movie>) {
        context.dataStore.edit { prefs ->
            prefs[MOVIES_KEY] = json.encodeToString(transform(decode(prefs[MOVIES_KEY])))
        }
    }

    private fun decode(raw: String?): List<Movie> =
        raw?.let { runCatching { json.decodeFromString<List<Movie>>(it) }.getOrNull() } ?: emptyList()
}
