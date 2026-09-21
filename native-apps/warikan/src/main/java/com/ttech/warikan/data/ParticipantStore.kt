package com.ttech.warikan.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.warikan.domain.Participant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("warikan")
private val PARTICIPANTS_KEY = stringPreferencesKey("participants")
private val json = Json { ignoreUnknownKeys = true }

/** 参加者リストを端末内に保存する(Webアプリ版の localStorage 保存に相当) */
class ParticipantStore(private val context: Context) {
    val participants: Flow<List<Participant>> = context.dataStore.data.map { prefs ->
        prefs[PARTICIPANTS_KEY]?.let { raw ->
            runCatching { json.decodeFromString<List<Participant>>(raw) }.getOrNull()
        } ?: defaultParticipants()
    }

    suspend fun save(participants: List<Participant>) {
        context.dataStore.edit { it[PARTICIPANTS_KEY] = json.encodeToString(participants) }
    }

    companion object {
        fun defaultParticipants(): List<Participant> = listOf(
            Participant(id = "1", name = "参加者1"),
            Participant(id = "2", name = "参加者2"),
        )
    }
}
