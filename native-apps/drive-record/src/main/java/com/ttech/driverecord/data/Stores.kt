package com.ttech.driverecord.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.driverecord.domain.DriveJson
import com.ttech.driverecord.domain.DriveSettings
import com.ttech.driverecord.domain.DriveSummary
import com.ttech.track.data.TrackFiles
import com.ttech.track.domain.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "drive_settings")

/** 設定の保存。既定値は [DriveSettings] のもの */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val autoRecord = booleanPreferencesKey("auto_record")
        val graceSec = intPreferencesKey("disconnect_grace_sec")
        val minDistance = intPreferencesKey("min_distance_m")
        val mapDark = booleanPreferencesKey("map_dark")
    }

    val settings: Flow<DriveSettings> = store.data.map { p ->
        val d = DriveSettings()
        DriveSettings(
            autoRecord = p[Keys.autoRecord] ?: d.autoRecord,
            disconnectGraceSec = p[Keys.graceSec] ?: d.disconnectGraceSec,
            minDistanceM = p[Keys.minDistance] ?: d.minDistanceM,
            mapStyleDark = p[Keys.mapDark] ?: d.mapStyleDark,
        )
    }

    suspend fun update(transform: (DriveSettings) -> DriveSettings) {
        store.edit { p ->
            val d = DriveSettings()
            val current = DriveSettings(
                autoRecord = p[Keys.autoRecord] ?: d.autoRecord,
                disconnectGraceSec = p[Keys.graceSec] ?: d.disconnectGraceSec,
                minDistanceM = p[Keys.minDistance] ?: d.minDistanceM,
                mapStyleDark = p[Keys.mapDark] ?: d.mapStyleDark,
            )
            val next = transform(current)
            p[Keys.autoRecord] = next.autoRecord
            p[Keys.graceSec] = next.disconnectGraceSec
            p[Keys.minDistance] = next.minDistanceM
            p[Keys.mapDark] = next.mapStyleDark
        }
    }
}

/** 記録の保管庫。一覧・詳細・削除・保存をまとめる */
class DriveRepository(val files: TrackFiles) {
    private val _drives = MutableStateFlow<List<DriveSummary>>(emptyList())
    val drives: StateFlow<List<DriveSummary>> = _drives.asStateFlow()

    /** 終わった記録を新しい順に読み直す。途中の記録(finished=false)は含めない */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _drives.value = files.ids().mapNotNull(::readSummary).filter { it.finished }.sortedByDescending { it.startTimeMs }
    }

    fun readSummary(id: String): DriveSummary? =
        files.readMeta(id)?.let { runCatching { DriveJson.decodeFromString<DriveSummary>(it) }.getOrNull() }

    suspend fun summary(id: String): DriveSummary? = withContext(Dispatchers.IO) { readSummary(id) }

    suspend fun track(id: String): List<TrackPoint> = withContext(Dispatchers.IO) { files.readTrack(id) }

    suspend fun save(summary: DriveSummary) = withContext(Dispatchers.IO) {
        files.writeMeta(summary.id, DriveJson.encodeToString(summary))
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
