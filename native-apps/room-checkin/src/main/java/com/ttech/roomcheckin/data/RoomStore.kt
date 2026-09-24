package com.ttech.roomcheckin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.roomcheckin.domain.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("room_checkin")
private val ROOMS_KEY = stringPreferencesKey("rooms")
private val json = Json { ignoreUnknownKeys = true }

/** 会議室・座席の利用状況を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class RoomStore(private val context: Context) {
    val rooms: Flow<List<Room>> = context.dataStore.data.map { decode(it[ROOMS_KEY]) }

    suspend fun add(item: Room) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Room>) -> List<Room>) {
        context.dataStore.edit { prefs ->
            prefs[ROOMS_KEY] = json.encodeToString(transform(decode(prefs[ROOMS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Room> =
        raw?.let { runCatching { json.decodeFromString<List<Room>>(it) }.getOrNull() } ?: emptyList()
}
