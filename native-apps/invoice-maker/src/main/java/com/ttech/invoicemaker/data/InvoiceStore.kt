package com.ttech.invoicemaker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.invoicemaker.domain.InvoiceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("invoice_maker")
private val DATA_KEY = stringPreferencesKey("invoice")
private val json = Json { ignoreUnknownKeys = true }

/** 作成中の見積書・請求書を端末内に残す(Webアプリ版の localStorage 保存に相当)。未保存ならnull。 */
class InvoiceStore(private val context: Context) {
    val data: Flow<InvoiceData?> = context.dataStore.data.map { prefs ->
        prefs[DATA_KEY]?.let { raw -> runCatching { json.decodeFromString<InvoiceData>(raw) }.getOrNull() }
    }

    suspend fun save(data: InvoiceData) {
        context.dataStore.edit { it[DATA_KEY] = json.encodeToString(data) }
    }
}
