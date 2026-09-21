package com.ttech.businesscardcontacts.data

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
data class Contact(
    val id: String,
    val name: String,
    val company: String,
    val title: String,
    val phone: String,
    val email: String,
)

private val Context.dataStore by preferencesDataStore("business_card_contacts")
private val CONTACTS_KEY = stringPreferencesKey("contacts")
private val json = Json { ignoreUnknownKeys = true }

/** 連絡先一覧を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class ContactStore(private val context: Context) {
    val contacts: Flow<List<Contact>> = context.dataStore.data.map { prefs -> decode(prefs[CONTACTS_KEY]) }

    suspend fun add(contact: Contact) {
        context.dataStore.edit { prefs ->
            prefs[CONTACTS_KEY] = json.encodeToString(listOf(contact) + decode(prefs[CONTACTS_KEY]))
        }
    }

    suspend fun remove(id: String) {
        context.dataStore.edit { prefs ->
            prefs[CONTACTS_KEY] = json.encodeToString(decode(prefs[CONTACTS_KEY]).filter { it.id != id })
        }
    }

    private fun decode(raw: String?): List<Contact> =
        raw?.let { runCatching { json.decodeFromString<List<Contact>>(it) }.getOrNull() } ?: emptyList()
}
