package com.ttech.colorpalette.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedPalette(val id: String, val colors: List<String>)

private val Context.dataStore by preferencesDataStore("color_palette")
private val SAVED_KEY = stringPreferencesKey("saved_palettes")
private val json = Json { ignoreUnknownKeys = true }

/** 保存した配色を端末内に残す(Webアプリ版の localStorage 保存に相当。最大20件)。 */
class SavedPaletteStore(private val context: Context) {
    val saved: Flow<List<SavedPalette>> = context.dataStore.data.map { prefs ->
        prefs[SAVED_KEY]?.let { raw ->
            runCatching { json.decodeFromString<List<SavedPalette>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun add(colors: List<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[SAVED_KEY]?.let { raw ->
                runCatching { json.decodeFromString<List<SavedPalette>>(raw) }.getOrNull()
            } ?: emptyList()
            val next = (listOf(SavedPalette(id = System.nanoTime().toString(), colors = colors)) + current).take(20)
            prefs[SAVED_KEY] = json.encodeToString(next)
        }
    }
}
