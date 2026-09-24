package com.ttech.plantwatering.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.plantwatering.domain.Plant
import com.ttech.plantwatering.domain.wateredOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore("plant_watering")
private val PLANTS_KEY = stringPreferencesKey("plants")
private val json = Json { ignoreUnknownKeys = true }

/** 植物の一覧を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class PlantStore(private val context: Context) {
    val plants: Flow<List<Plant>> = context.dataStore.data.map { decode(it[PLANTS_KEY]) }

    suspend fun current(): List<Plant> = plants.first()

    suspend fun add(plant: Plant) {
        context.dataStore.edit { prefs ->
            prefs[PLANTS_KEY] = json.encodeToString(listOf(plant) + decode(prefs[PLANTS_KEY]))
        }
    }

    suspend fun waterNow(id: String, today: LocalDate) {
        context.dataStore.edit { prefs ->
            val updated = decode(prefs[PLANTS_KEY]).map { if (it.id == id) it.wateredOn(today) else it }
            prefs[PLANTS_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[PLANTS_KEY] = json.encodeToString(decode(prefs[PLANTS_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<Plant> =
        raw?.let { runCatching { json.decodeFromString<List<Plant>>(it) }.getOrNull() } ?: emptyList()
}
