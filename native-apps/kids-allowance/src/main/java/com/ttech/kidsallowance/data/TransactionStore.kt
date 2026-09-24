package com.ttech.kidsallowance.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.kidsallowance.domain.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("kids_allowance")
private val TRANSACTIONS_KEY = stringPreferencesKey("transactions")
private val json = Json { ignoreUnknownKeys = true }

/** お小遣いの出入りを端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class TransactionStore(private val context: Context) {
    val transactions: Flow<List<Transaction>> = context.dataStore.data.map { decode(it[TRANSACTIONS_KEY]) }

    suspend fun add(item: Transaction) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Transaction>) -> List<Transaction>) {
        context.dataStore.edit { prefs ->
            prefs[TRANSACTIONS_KEY] = json.encodeToString(transform(decode(prefs[TRANSACTIONS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Transaction> =
        raw?.let { runCatching { json.decodeFromString<List<Transaction>>(it) }.getOrNull() } ?: emptyList()
}
