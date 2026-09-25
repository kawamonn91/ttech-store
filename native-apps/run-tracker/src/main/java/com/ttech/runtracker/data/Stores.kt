package com.ttech.runtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.runtracker.domain.Body
import com.ttech.runtracker.domain.RunJson
import com.ttech.runtracker.domain.RunSettings
import com.ttech.runtracker.domain.RunSummary
import com.ttech.runtracker.domain.WeightEntry
import com.ttech.track.data.TrackFiles
import com.ttech.track.domain.TrackPoint
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "run_settings")

/** 設定と、身長の保存。既定値は [RunSettings] のもの */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val minDistance = intPreferencesKey("min_distance_m")
        val mapDark = booleanPreferencesKey("map_dark")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val heightCm = doublePreferencesKey("height_cm")
    }

    private fun Preferences.toSettings(): RunSettings {
        val d = RunSettings()
        return RunSettings(
            minDistanceM = this[Keys.minDistance] ?: d.minDistanceM,
            mapStyleDark = this[Keys.mapDark] ?: d.mapStyleDark,
            keepScreenOn = this[Keys.keepScreenOn] ?: d.keepScreenOn,
        )
    }

    val settings: Flow<RunSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (RunSettings) -> RunSettings) {
        store.edit { p ->
            val next = transform(p.toSettings())
            p[Keys.minDistance] = next.minDistanceM
            p[Keys.mapDark] = next.mapStyleDark
            p[Keys.keepScreenOn] = next.keepScreenOn
        }
    }

    /** 身長(cm)。未入力なら null */
    val heightCm: Flow<Double?> = store.data.map { it[Keys.heightCm] }

    suspend fun setHeight(cm: Double?) {
        store.edit { p -> if (cm == null) p.remove(Keys.heightCm) else p[Keys.heightCm] = cm }
    }
}

/** ランの記録の保管庫。一覧・詳細・削除・保存をまとめる */
class RunRepository(val files: TrackFiles) {
    private val _runs = MutableStateFlow<List<RunSummary>>(emptyList())
    val runs: StateFlow<List<RunSummary>> = _runs.asStateFlow()

    /** 終わった記録を新しい順に読み直す。途中の記録(finished=false)は含めない */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _runs.value = files.ids().mapNotNull(::readSummary).filter { it.finished }.sortedByDescending { it.startTimeMs }
    }

    fun readSummary(id: String): RunSummary? =
        files.readMeta(id)?.let { runCatching { RunJson.decodeFromString<RunSummary>(it) }.getOrNull() }

    suspend fun summary(id: String): RunSummary? = withContext(Dispatchers.IO) { readSummary(id) }

    suspend fun track(id: String): List<TrackPoint> = withContext(Dispatchers.IO) { files.readTrack(id) }

    suspend fun save(summary: RunSummary) = withContext(Dispatchers.IO) {
        files.writeMeta(summary.id, RunJson.encodeToString(summary))
        refresh()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        files.delete(id)
        refresh()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        files.ids().forEach(files::delete)
        refresh()
    }
}

/**
 * 体重の記録の保管庫。1つのファイル(JSON)に、日付順に並べて持つ。
 * 記録は少ない(1日1件ほど)ので、まるごと読み書きする。
 */
class BodyRepository(private val file: File) {
    private val _entries = MutableStateFlow(read())
    /** 新しい順 */
    val entries: StateFlow<List<WeightEntry>> = _entries.asStateFlow()

    private fun read(): List<WeightEntry> = runCatching {
        if (!file.exists()) emptyList()
        else RunJson.decodeFromString(ListSerializer(WeightEntry.serializer()), file.readText())
    }.getOrDefault(emptyList()).sortedByDescending { it.timeMs }

    private fun write(list: List<WeightEntry>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(RunJson.encodeToString(ListSerializer(WeightEntry.serializer()), list))
        if (!tmp.renameTo(file)) file.writeText(tmp.readText())
        _entries.value = list.sortedByDescending { it.timeMs }
    }

    /** 同じ時刻の記録があれば置き換える。範囲外の体重は加えない(false) */
    suspend fun add(entry: WeightEntry): Boolean = withContext(Dispatchers.IO) {
        if (entry.weightKg !in Body.WEIGHT_RANGE) return@withContext false
        write(_entries.value.filter { it.timeMs != entry.timeMs } + entry)
        true
    }

    suspend fun remove(timeMs: Long) = withContext(Dispatchers.IO) {
        write(_entries.value.filter { it.timeMs != timeMs })
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        _entries.value = emptyList()
    }

    /** その時点の体重(記録が無ければ null) */
    fun weightAt(timeMs: Long): Double? = Body.weightAt(_entries.value, timeMs)
}
