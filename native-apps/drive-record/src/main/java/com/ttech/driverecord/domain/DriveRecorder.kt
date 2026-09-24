package com.ttech.driverecord.domain

import com.ttech.track.data.TrackFiles
import com.ttech.track.data.TrackWriter
import com.ttech.track.domain.FixDecision
import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.TrackFilter
import com.ttech.track.domain.TrackPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 記録中の画面に出す、いまの状況 */
data class LiveDrive(
    val id: String,
    val trigger: String,
    val startTimeMs: Long,
    val points: Int,
    val rejected: Int,
    val distanceM: Double,
    val speedMps: Double,
    val maxSpeedMps: Double,
    val accuracyM: Double?,
    val lastPoint: TrackPoint?,
    val updatedAtMs: Long,
) {
    fun elapsedMs(nowMs: Long): Long = (nowMs - startTimeMs).coerceAtLeast(0)
}

val DriveJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 1回のドライブの記録。GPSの点を受け取り、おかしい点を除いて、ファイルに追記していく。
 * 途中でアプリが止まっても、点はファイルに残っているので、[recover] で概要を作り直せる。
 */
class DriveRecorder(
    private val files: TrackFiles,
    private val filter: TrackFilter = TrackFilter(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var id: String? = null
    private var trigger: String = Trigger.MANUAL
    private var writer: TrackWriter? = null
    private var last: TrackPoint? = null
    private val points = ArrayList<TrackPoint>()
    private var rejected = 0
    private var distance = 0.0
    private var maxSpeed = 0.0
    private var startMs = 0L

    val isActive: Boolean get() = id != null

    /** 記録を始める。すでに記録中なら、その ID をそのまま返す */
    fun start(trigger: String): String {
        id?.let { return it }
        startMs = clock()
        val newId = startMs.toString()
        id = newId
        this.trigger = trigger
        writer = files.openWriter(newId)
        // 途中で止まったときに、きっかけ(自動・手動)が分かるように、開始の時点で概要を書いておく
        files.writeMeta(newId, DriveJson.encodeToString(DriveSummary(id = newId, startTimeMs = startMs, endTimeMs = startMs, trigger = trigger, finished = false)))
        return newId
    }

    /** 点を1つ受け取る。記録に加えたら true、除いたら false */
    fun onPoint(p: TrackPoint): Boolean {
        if (id == null) return false
        when (filter.decide(last, p)) {
            is FixDecision.Reject -> {
                rejected++
                return false
            }
            FixDecision.Accept -> Unit
        }
        val prev = last
        if (prev != null) {
            val d = GeoMath.distanceMeters(prev.latLon, p.latLon)
            val stationary = (p.speed ?: 0.0) < 0.5 && (prev.speed ?: 0.0) < 0.5 && d < maxOf(3.0, p.hAcc ?: 0.0)
            if (!stationary) distance += d
        }
        p.speed?.let { if (it > maxSpeed && it < 90) maxSpeed = it }
        writer?.append(p)
        points.add(p)
        last = p
        return true
    }

    fun live(): LiveDrive? {
        val currentId = id ?: return null
        val l = last
        return LiveDrive(
            id = currentId, trigger = trigger, startTimeMs = startMs, points = points.size, rejected = rejected,
            distanceM = distance, speedMps = l?.speed ?: 0.0, maxSpeedMps = maxSpeed, accuracyM = l?.hAcc, lastPoint = l,
            updatedAtMs = clock(),
        )
    }

    /**
     * 記録を終える。短すぎる(または点が無い)ドライブは記録に残さず、null を返す。
     * 残すときは、統計を計算して概要を保存し、その概要を返す。
     */
    fun finish(minDistanceM: Int): DriveSummary? {
        val currentId = id ?: return null
        writer?.close()
        writer = null
        id = null
        val all = points.toList()
        reset()

        val stats = DriveStatsCalculator.compute(all)
        if (all.size < 2 || stats.distanceM < minDistanceM) {
            files.delete(currentId)
            return null
        }
        val summary = DriveSummary.from(currentId, stats, all, trigger, finished = true)
        files.writeMeta(currentId, DriveJson.encodeToString(summary))
        return summary
    }

    private fun reset() {
        points.clear()
        last = null
        rejected = 0
        distance = 0.0
        maxSpeed = 0.0
    }

    companion object {
        /**
         * 記録の途中でアプリが終了した(終わっていない)記録を、ファイルの点から復旧する。
         * 短すぎるものは捨てる。復旧した概要の一覧を返す。
         */
        fun recover(files: TrackFiles, minDistanceM: Int, activeId: String? = null): List<DriveSummary> {
            val recovered = ArrayList<DriveSummary>()
            for (id in files.ids()) {
                if (id == activeId) continue
                val meta = files.readMeta(id)?.let { runCatching { DriveJson.decodeFromString<DriveSummary>(it) }.getOrNull() }
                if (meta != null && meta.finished) continue
                val points = files.readTrack(id)
                val stats = DriveStatsCalculator.compute(points)
                if (points.size < 2 || stats.distanceM < minDistanceM) {
                    files.delete(id)
                    continue
                }
                val summary = DriveSummary.from(id, stats, points, meta?.trigger ?: Trigger.MANUAL, finished = true)
                    .copy(startLabel = meta?.startLabel, endLabel = meta?.endLabel)
                files.writeMeta(id, DriveJson.encodeToString(summary))
                recovered.add(summary)
            }
            return recovered
        }
    }
}

/** 地名(住所)の表示用の整形 */
object PlaceLabel {
    /** 「日本、〒965-0000 福島県会津若松市…」→「福島県会津若松市…」 */
    fun shorten(addressLine: String?): String? {
        if (addressLine.isNullOrBlank()) return null
        var s = addressLine.trim()
        s = s.removePrefix("日本、").removePrefix("日本,").removePrefix("Japan, ").trim()
        s = s.replace(Regex("^〒?\\s*\\d{3}-?\\d{4}\\s*"), "")
        return s.ifBlank { null }
    }
}
