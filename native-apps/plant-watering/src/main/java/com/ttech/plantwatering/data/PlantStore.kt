package com.ttech.plantwatering.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.plantwatering.domain.Plant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("plant_watering")
private val PLANTS_KEY = stringPreferencesKey("plants")
private val json = Json { ignoreUnknownKeys = true }

/** 登録した植物と最終水やり日を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class PlantStore(private val context: Context) {
    val plants: Flow<List<Plant>> = context.dataStore.data.map { decode(it[PLANTS_KEY]) }

    suspend fun add(item: Plant) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Plant>) -> List<Plant>) {
        context.dataStore.edit { prefs ->
            prefs[PLANTS_KEY] = json.encodeToString(transform(decode(prefs[PLANTS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Plant> =
        raw?.let { runCatching { json.decodeFromString<List<Plant>>(it) }.getOrNull() } ?: emptyList()
}
