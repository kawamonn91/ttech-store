package com.ttech.driverecord.domain

import com.ttech.track.data.TrackFiles
import com.ttech.track.data.TrackRecorder
import com.ttech.track.domain.IdleTrim
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

/** 車が「動いている」とみなす速度(m/s)。これ未満が続く出発前・到着後は、記録から除く */
private const val CAR_MOVING_MPS = 1.5

/**
 * 1回のドライブの記録。点の受け取り・除去・ファイルへの追記は共通の [TrackRecorder] に任せ、
 * ここでは、きっかけ(自動・手動)の記録と、終了時の統計・概要の作成をする。
 * 途中でアプリが止まっても、点はファイルに残っているので、[recover] で概要を作り直せる。
 */
class DriveRecorder(
    private val files: TrackFiles,
    filter: TrackFilter = TrackFilter(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recorder = TrackRecorder(files, filter, clock)
    private var trigger: String = Trigger.MANUAL

    val isActive: Boolean get() = recorder.isActive

    /** 記録を始める。すでに記録中なら、その ID をそのまま返す */
    fun start(trigger: String): String {
        recorder.currentId?.let { return it }
        this.trigger = trigger
        val id = recorder.start()
        val startMs = id.toLong()
        // 途中で止まったときに、きっかけ(自動・手動)が分かるように、開始の時点で概要を書いておく
        files.writeMeta(id, DriveJson.encodeToString(DriveSummary(id = id, startTimeMs = startMs, endTimeMs = startMs, trigger = trigger, finished = false)))
        return id
    }

    /** 点を1つ受け取る。記録に加えたら true、除いたら false */
    fun onPoint(p: TrackPoint): Boolean = recorder.onPoint(p)

    fun live(): LiveDrive? = recorder.live()?.let {
        LiveDrive(
            id = it.id, trigger = trigger, startTimeMs = it.startTimeMs, points = it.points, rejected = it.rejected,
            distanceM = it.distanceM, speedMps = it.speedMps, maxSpeedMps = it.maxSpeedMps, accuracyM = it.accuracyM,
            lastPoint = it.lastPoint, updatedAtMs = it.updatedAtMs,
        )
    }

    /**
     * 記録を終える。短すぎる(または点が無い)ドライブは記録に残さず、null を返す。
     * 残すときは、出発前・到着後の停止を除いて統計を計算し、概要を保存して返す。
     */
    fun finish(minDistanceM: Int): DriveSummary? {
        val finished = recorder.stop(idleMovingMps = CAR_MOVING_MPS) ?: return null
        val all = finished.points
        val stats = DriveStatsCalculator.compute(all)
        if (all.size < 2 || stats.distanceM < minDistanceM) {
            files.delete(finished.id)
            return null
        }
        val summary = DriveSummary.from(finished.id, stats, all, trigger, finished = true)
        files.writeMeta(finished.id, DriveJson.encodeToString(summary))
        return summary
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
                val raw = files.readTrack(id)
                val points = IdleTrim.trim(raw, CAR_MOVING_MPS)
                if (points.size != raw.size) {
                    files.csvFile(id).delete()
                    files.openWriter(id).use { w -> points.forEach(w::append) }
                }
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
