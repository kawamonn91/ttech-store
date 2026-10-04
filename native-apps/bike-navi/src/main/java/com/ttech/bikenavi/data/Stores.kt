package com.ttech.bikenavi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.bikenavi.domain.BikeJson
import com.ttech.bikenavi.domain.BikeSettings
import com.ttech.bikenavi.domain.Place
import com.ttech.bikenavi.domain.RouteStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "bike_settings")

/** 設定の保存。既定値は [BikeSettings] のもの */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val voice = booleanPreferencesKey("voice")
        val weather = booleanPreferencesKey("weather_briefing")
        val rate = floatPreferencesKey("speech_rate")
        val headingUp = booleanPreferencesKey("heading_up")
        val mapDark = booleanPreferencesKey("map_dark")
        val routeStyle = stringPreferencesKey("route_style")
        val paceKmh = doublePreferencesKey("pace_kmh")
        val restIntervalHours = doublePreferencesKey("rest_interval_hours")
    }

    private fun read(p: Preferences): BikeSettings {
        val d = BikeSettings()
        return BikeSettings(
            voice = p[Keys.voice] ?: d.voice,
            weatherBriefing = p[Keys.weather] ?: d.weatherBriefing,
            speechRate = p[Keys.rate] ?: d.speechRate,
            headingUp = p[Keys.headingUp] ?: d.headingUp,
            mapDark = p[Keys.mapDark] ?: d.mapDark,
            routeStyle = p[Keys.routeStyle]?.let { name -> RouteStyle.entries.find { it.name == name } } ?: d.routeStyle,
            paceKmh = p[Keys.paceKmh] ?: d.paceKmh,
            restIntervalHours = p[Keys.restIntervalHours] ?: d.restIntervalHours,
        )
    }

    val settings: Flow<BikeSettings> = store.data.map(::read)

    suspend fun current(): BikeSettings = read(store.data.first())

    suspend fun update(transform: (BikeSettings) -> BikeSettings) {
        store.edit { p ->
            val next = transform(read(p))
            p[Keys.voice] = next.voice
            p[Keys.weather] = next.weatherBriefing
            p[Keys.rate] = next.speechRate
            p[Keys.headingUp] = next.headingUp
            p[Keys.mapDark] = next.mapDark
            p[Keys.routeStyle] = next.routeStyle.name
            p[Keys.paceKmh] = next.paceKmh
            p[Keys.restIntervalHours] = next.restIntervalHours
        }
    }
}

private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore(name = "bike_recent")

/** 最近の目的地(端末の中だけに保存。新しい順に、最大10件) */
class RecentPlaces(context: Context) {
    private val store = context.applicationContext.recentDataStore
    private val key = stringPreferencesKey("places")
    private val serializer = ListSerializer(Place.serializer())

    val places: Flow<List<Place>> = store.data.map { p -> decode(p[key]) }

    private fun decode(text: String?): List<Place> =
        text?.let { runCatching { BikeJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    suspend fun add(place: Place) {
        store.edit { p ->
            val list = decode(p[key]).filterNot { it.name == place.name && kotlin.math.abs(it.lat - place.lat) < 0.001 && kotlin.math.abs(it.lon - place.lon) < 0.001 }
            p[key] = BikeJson.encodeToString(serializer, (listOf(place) + list).take(MAX))
        }
    }

    suspend fun remove(place: Place) {
        store.edit { p -> p[key] = BikeJson.encodeToString(serializer, decode(p[key]).filterNot { it == place }) }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }

    companion object {
        const val MAX = 10
    }
}
