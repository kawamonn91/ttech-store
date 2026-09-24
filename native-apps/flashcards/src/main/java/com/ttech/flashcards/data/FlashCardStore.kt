package com.ttech.flashcards.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.flashcards.domain.FlashCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("flashcards")
private val CARDS_KEY = stringPreferencesKey("deck")
private val json = Json { ignoreUnknownKeys = true }

/** 単語帳を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class FlashCardStore(private val context: Context) {
    val cards: Flow<List<FlashCard>> = context.dataStore.data.map { decode(it[CARDS_KEY]) }

    /** Webアプリ版と同じく末尾に追加する。 */
    suspend fun add(card: FlashCard) {
        context.dataStore.edit { prefs ->
            prefs[CARDS_KEY] = json.encodeToString(decode(prefs[CARDS_KEY]) + card)
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[CARDS_KEY] = json.encodeToString(decode(prefs[CARDS_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<FlashCard> =
        raw?.let { runCatching { json.decodeFromString<List<FlashCard>>(it) }.getOrNull() } ?: emptyList()
}
