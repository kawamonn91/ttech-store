package com.ttech.propertychecklist.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.propertychecklist.domain.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("property_checklist")
private val PROPERTIES_KEY = stringPreferencesKey("properties")
private val json = Json { ignoreUnknownKeys = true }

/** 物件ごとの内見チェックリストを端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class PropertyStore(private val context: Context) {
    val properties: Flow<List<Property>> = context.dataStore.data.map { decode(it[PROPERTIES_KEY]) }

    suspend fun add(item: Property) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Property>) -> List<Property>) {
        context.dataStore.edit { prefs ->
            prefs[PROPERTIES_KEY] = json.encodeToString(transform(decode(prefs[PROPERTIES_KEY])))
        }
    }

    private fun decode(raw: String?): List<Property> =
        raw?.let { runCatching { json.decodeFromString<List<Property>>(it) }.getOrNull() } ?: emptyList()
}
