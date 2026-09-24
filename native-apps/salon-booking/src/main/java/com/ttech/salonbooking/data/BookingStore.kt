package com.ttech.salonbooking.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.salonbooking.domain.Booking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("salon_booking")
private val BOOKINGS_KEY = stringPreferencesKey("bookings")
private val json = Json { ignoreUnknownKeys = true }

/** 予約台帳を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class BookingStore(private val context: Context) {
    val bookings: Flow<List<Booking>> = context.dataStore.data.map { decode(it[BOOKINGS_KEY]) }

    suspend fun add(item: Booking) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Booking>) -> List<Booking>) {
        context.dataStore.edit { prefs ->
            prefs[BOOKINGS_KEY] = json.encodeToString(transform(decode(prefs[BOOKINGS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Booking> =
        raw?.let { runCatching { json.decodeFromString<List<Booking>>(it) }.getOrNull() } ?: emptyList()
}
