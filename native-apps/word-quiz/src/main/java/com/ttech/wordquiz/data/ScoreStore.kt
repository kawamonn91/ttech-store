package com.ttech.wordquiz.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.wordquiz.domain.Score
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("word_quiz")
private val KANJI_KEY = intPreferencesKey("score_kanji")
private val EIKEN_KEY = intPreferencesKey("score_eiken")

/** デッキごとの正解数を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class ScoreStore(private val context: Context) {
    val score: Flow<Score> = context.dataStore.data.map { Score(kanji = it[KANJI_KEY] ?: 0, eiken = it[EIKEN_KEY] ?: 0) }

    suspend fun update(transform: (Score) -> Score) {
        context.dataStore.edit { prefs ->
            val next = transform(Score(kanji = prefs[KANJI_KEY] ?: 0, eiken = prefs[EIKEN_KEY] ?: 0))
            prefs[KANJI_KEY] = next.kanji
            prefs[EIKEN_KEY] = next.eiken
        }
    }
}
