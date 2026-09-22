package com.ttech.expensetracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.expensetracker.domain.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("expense_tracker")
private val EXPENSES_KEY = stringPreferencesKey("expenses")
private val json = Json { ignoreUnknownKeys = true }

/** 家計簿の記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class ExpenseStore(private val context: Context) {
    val expenses: Flow<List<Expense>> = context.dataStore.data.map { decode(it[EXPENSES_KEY]) }

    suspend fun add(expense: Expense) {
        context.dataStore.edit { prefs ->
            prefs[EXPENSES_KEY] = json.encodeToString(listOf(expense) + decode(prefs[EXPENSES_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[EXPENSES_KEY] = json.encodeToString(decode(prefs[EXPENSES_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<Expense> =
        raw?.let { runCatching { json.decodeFromString<List<Expense>>(it) }.getOrNull() } ?: emptyList()
}
