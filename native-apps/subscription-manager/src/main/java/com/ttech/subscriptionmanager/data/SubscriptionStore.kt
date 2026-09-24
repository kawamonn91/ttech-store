package com.ttech.subscriptionmanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.subscriptionmanager.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("subscription_manager")
private val SUBSCRIPTIONS_KEY = stringPreferencesKey("subscriptions")
private val json = Json { ignoreUnknownKeys = true }

/** 登録中のサブスクを端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class SubscriptionStore(private val context: Context) {
    val subscriptions: Flow<List<Subscription>> = context.dataStore.data.map { decode(it[SUBSCRIPTIONS_KEY]) }

    suspend fun add(item: Subscription) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Subscription>) -> List<Subscription>) {
        context.dataStore.edit { prefs ->
            prefs[SUBSCRIPTIONS_KEY] = json.encodeToString(transform(decode(prefs[SUBSCRIPTIONS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Subscription> =
        raw?.let { runCatching { json.decodeFromString<List<Subscription>>(it) }.getOrNull() } ?: emptyList()
}
