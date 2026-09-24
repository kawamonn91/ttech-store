package com.ttech.receipttracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.receipttracker.domain.Receipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("receipt_tracker")
private val RECEIPTS_KEY = stringPreferencesKey("receipts")
private val json = Json { ignoreUnknownKeys = true }

/** 経費レシートの記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class ReceiptStore(private val context: Context) {
    val receipts: Flow<List<Receipt>> = context.dataStore.data.map { decode(it[RECEIPTS_KEY]) }

    suspend fun add(item: Receipt) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Receipt>) -> List<Receipt>) {
        context.dataStore.edit { prefs ->
            prefs[RECEIPTS_KEY] = json.encodeToString(transform(decode(prefs[RECEIPTS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Receipt> =
        raw?.let { runCatching { json.decodeFromString<List<Receipt>>(it) }.getOrNull() } ?: emptyList()
}
