package com.ttech.campchecklist.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.campchecklist.domain.DEFAULT_ITEM_NAMES
import com.ttech.campchecklist.domain.GearItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore("camp_checklist")
private val ITEMS_KEY = stringPreferencesKey("items")
private val json = Json { ignoreUnknownKeys = true }

/** チェックリストを端末内に残す(Webアプリ版のlocalStorage保存に相当)。 */
class GearStore(private val context: Context) {
    val items: Flow<List<GearItem>> = context.dataStore.data.map { prefs ->
        prefs[ITEMS_KEY]?.let { raw -> runCatching { json.decodeFromString<List<GearItem>>(raw) }.getOrNull() }
            ?: DEFAULT_ITEM_NAMES.map { GearItem(id = UUID.randomUUID().toString(), name = it) }
    }

    suspend fun save(items: List<GearItem>) {
        context.dataStore.edit { it[ITEMS_KEY] = json.encodeToString(items) }
    }
}
