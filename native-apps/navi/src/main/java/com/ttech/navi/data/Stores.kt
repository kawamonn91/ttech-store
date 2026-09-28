package com.ttech.navi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.navi.domain.NaviJson
import com.ttech.navi.domain.NaviSettings
import com.ttech.navi.domain.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "navi_settings")

/** 設定の保存。既定値は [NaviSettings] のもの */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val voice = booleanPreferencesKey("voice")
        val weather = booleanPreferencesKey("weather_briefing")
        val region = booleanPreferencesKey("region_announcements")
        val rate = floatPreferencesKey("speech_rate")
        val headingUp = booleanPreferencesKey("heading_up")
        val mapDark = booleanPreferencesKey("map_dark")
    }

    private fun read(p: Preferences): NaviSettings {
        val d = NaviSettings()
        return NaviSettings(
            voice = p[Keys.voice] ?: d.voice,
            weatherBriefing = p[Keys.weather] ?: d.weatherBriefing,
            regionAnnouncements = p[Keys.region] ?: d.regionAnnouncements,
            speechRate = p[Keys.rate] ?: d.speechRate,
            headingUp = p[Keys.headingUp] ?: d.headingUp,
            mapDark = p[Keys.mapDark] ?: d.mapDark,
        )
    }

    val settings: Flow<NaviSettings> = store.data.map(::read)

    suspend fun current(): NaviSettings = read(store.data.first())

    suspend fun update(transform: (NaviSettings) -> NaviSettings) {
        store.edit { p ->
            val next = transform(read(p))
            p[Keys.voice] = next.voice
            p[Keys.weather] = next.weatherBriefing
            p[Keys.region] = next.regionAnnouncements
            p[Keys.rate] = next.speechRate
            p[Keys.headingUp] = next.headingUp
            p[Keys.mapDark] = next.mapDark
        }
    }
}

private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore(name = "navi_recent")

/** 最近の目的地(端末の中だけに保存。新しい順に、最大10件) */
class RecentPlaces(context: Context) {
    private val store = context.applicationContext.recentDataStore
    private val key = stringPreferencesKey("places")
    private val serializer = ListSerializer(Place.serializer())

    val places: Flow<List<Place>> = store.data.map { p -> decode(p[key]) }

    private fun decode(text: String?): List<Place> =
        text?.let { runCatching { NaviJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    suspend fun add(place: Place) {
        store.edit { p ->
            val list = decode(p[key]).filterNot { it.name == place.name && kotlin.math.abs(it.lat - place.lat) < 0.001 && kotlin.math.abs(it.lon - place.lon) < 0.001 }
            p[key] = NaviJson.encodeToString(serializer, (listOf(place) + list).take(MAX))
        }
    }

    suspend fun remove(place: Place) {
        store.edit { p -> p[key] = NaviJson.encodeToString(serializer, decode(p[key]).filterNot { it == place }) }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }

    companion object {
        const val MAX = 10
    }
}
