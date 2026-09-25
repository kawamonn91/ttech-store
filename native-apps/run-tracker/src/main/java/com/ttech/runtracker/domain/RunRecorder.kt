package com.ttech.runtracker.domain

import com.ttech.track.data.TrackFiles
import com.ttech.track.data.TrackRecorder
import com.ttech.track.domain.IdleTrim
import com.ttech.track.domain.TrackFilter
import com.ttech.track.domain.TrackPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 計測中の画面に出す、いまの状況 */
data class LiveRun(
    val id: String,
    val startTimeMs: Long,
    val points: Int,
    val rejected: Int,
    val distanceM: Double,
    /** ならした現在の速度(m/s)。ペースの表示に使う */
    val speedMps: Double,
    val accuracyM: Double?,
    val lastPoint: TrackPoint?,
    val paused: Boolean,
    /** すでに終わった一時停止の合計(ms) */
    val pausedClosedMs: Long,
    /** いま一時停止中なら、その開始時刻 */
    val pauseStartMs: Long?,
    val updatedAtMs: Long,
) {
    /** 開始からの経過(一時停止も含む) */
    fun elapsedMs(nowMs: Long): Long = (nowMs - startTimeMs).coerceAtLeast(0)

    /** 走っている時間(一時停止を除く)。一時停止中は止まったまま */
    fun activeMs(nowMs: Long): Long {
        val ongoing = pauseStartMs?.let { nowMs - it } ?: 0L
        return (nowMs - startTimeMs - pausedClosedMs - ongoing).coerceAtLeast(0)
    }

    /** 現在のペース(秒/km)。ほぼ止まっているときは null */
    val paceSecPerKm: Double? get() = if (speedMps >= MIN_SPEED_FOR_PACE) 1000.0 / speedMps else null

    /** 平均ペース(秒/km)。まだ走っていなければ null */
    fun avgPaceSecPerKm(nowMs: Long): Double? =
        if (distanceM >= 20.0) activeMs(nowMs) / 1000.0 / (distanceM / 1000.0) else null

    private companion object {
        const val MIN_SPEED_FOR_PACE = 0.6
    }
}

val RunJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** 走っている(歩いている)とみなす速度(m/s)。これ未満が続く出発前・終了後は、記録から除く */
private const val RUN_MOVING_MPS = RunStatsCalculator.MOVING_MPS

/** ランナーとして現実的でない速さ(m/s)。これを超えて「飛んだ」点は、測位の誤りとして除く(時速54km) */
private const val RUN_MAX_SPEED_MPS = 15.0

/** 現在の速度をならす強さ(0〜1。大きいほど、いまの値に素早く追いつく) */
private const val SPEED_SMOOTHING = 0.25

/**
 * 1回のランの記録。点の受け取り・除去・ファイルへの追記は共通の [TrackRecorder] に任せ、
 * ここでは、一時停止の時間帯の記録と、終了時の統計・概要の作成をする。
 * 途中でアプリが止まっても、点と一時停止の時間帯はファイルに残っているので、[recover] で概要を作り直せる。
 */
class RunRecorder(
    private val files: TrackFiles,
    filter: TrackFilter = TrackFilter(maxSpeedMps = RUN_MAX_SPEED_MPS),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recorder = TrackRecorder(files, filter, clock)
    private val pauses = ArrayList<RunPause>()
    private var pauseStartMs: Long? = null
    private var smoothSpeed = 0.0
    private var hasSpeed = false

    val isActive: Boolean get() = recorder.isActive
    val isPaused: Boolean get() = recorder.isPaused

    /** 記録を始める。すでに記録中なら、その ID をそのまま返す */
    fun start(): String {
        recorder.currentId?.let { return it }
        val id = recorder.start()
        pauses.clear()
        pauseStartMs = null
        smoothSpeed = 0.0
        hasSpeed = false
        writeMeta(id)
        return id
    }

    fun pause() {
        val id = recorder.currentId ?: return
        if (recorder.isPaused) return
        recorder.pause()
        pauseStartMs = clock()
        smoothSpeed = 0.0
        hasSpeed = false
        writeMeta(id)
    }

    fun resume() {
        val id = recorder.currentId ?: return
        if (!recorder.isPaused) return
        recorder.resume()
        closePause()
        writeMeta(id)
    }

    /** 点を1つ受け取る。記録に加えたら true、除いた(または一時停止中)なら false */
    fun onPoint(p: TrackPoint): Boolean {
        val accepted = recorder.onPoint(p)
        if (accepted) p.speed?.let {
            smoothSpeed = if (hasSpeed) smoothSpeed + SPEED_SMOOTHING * (it - smoothSpeed) else it
            hasSpeed = true
        }
        return accepted
    }

    fun live(): LiveRun? = recorder.live()?.let {
        LiveRun(
            id = it.id, startTimeMs = it.startTimeMs, points = it.points, rejected = it.rejected, distanceM = it.distanceM,
            speedMps = smoothSpeed, accuracyM = it.accuracyM, lastPoint = it.lastPoint, paused = it.paused,
            pausedClosedMs = pauses.sumOf { p -> p.endMs - p.startMs }, pauseStartMs = pauseStartMs, updatedAtMs = it.updatedAtMs,
        )
    }

    /**
     * 記録を終える。短すぎる(または点が無い)ランは記録に残さず、null を返す。
     * 残すときは、出発前・終了後の停止を除いて統計を計算し、概要を保存して返す。
     * @param weightKg 消費カロリーの計算に使う体重。無ければ標準の体重で計算する
     */
    fun finish(minDistanceM: Int, weightKg: Double?): RunSummary? {
        if (recorder.isPaused) closePause()
        val finished = recorder.stop(idleMovingMps = RUN_MOVING_MPS) ?: return null
        val recordedPauses = pauses.toList()
        pauses.clear()
        val summary = summarize(finished.id, finished.points, recordedPauses, weightKg)
        if (finished.points.size < 2 || summary.distanceM < minDistanceM) {
            files.delete(finished.id)
            return null
        }
        files.writeMeta(finished.id, RunJson.encodeToString(summary))
        return summary
    }

    private fun closePause() {
        val start = pauseStartMs ?: return
        pauses.add(RunPause(start, clock()))
        pauseStartMs = null
    }

    /** 途中の状態(一時停止の時間帯)をファイルに残す。アプリが止まっても、あとで正しく数え直せるように */
    private fun writeMeta(id: String) {
        val startMs = id.toLongOrNull() ?: 0L
        val open = pauseStartMs?.let { listOf(RunPause(it, clock())) } ?: emptyList()
        files.writeMeta(id, RunJson.encodeToString(RunSummary(id = id, startTimeMs = startMs, endTimeMs = startMs, pauses = pauses + open, finished = false)))
    }

    companion object {
        fun summarize(id: String, points: List<TrackPoint>, pauses: List<RunPause>, weightKg: Double?): RunSummary {
            val stats = RunStatsCalculator.compute(points, pauses, weightKg ?: Calories.DEFAULT_WEIGHT_KG)
            return RunSummary.from(id, stats, points, pauses, weightKg, finished = true)
        }

        /**
         * 記録の途中でアプリが終了した(終わっていない)記録を、ファイルの点から復旧する。
         * 短すぎるものは捨てる。復旧した概要の一覧を返す。
         */
        fun recover(files: TrackFiles, minDistanceM: Int, weightAt: (Long) -> Double?, activeId: String? = null): List<RunSummary> {
            val recovered = ArrayList<RunSummary>()
            for (id in files.ids()) {
                if (id == activeId) continue
                val meta = files.readMeta(id)?.let { runCatching { RunJson.decodeFromString<RunSummary>(it) }.getOrNull() }
                if (meta != null && meta.finished) continue
                val raw = files.readTrack(id)
                val points = IdleTrim.trim(raw, RUN_MOVING_MPS)
                if (points.size != raw.size) {
                    files.csvFile(id).delete()
                    files.openWriter(id).use { w -> points.forEach(w::append) }
                }
                val startMs = points.firstOrNull()?.timeMs ?: id.toLongOrNull() ?: 0L
                val summary = summarize(id, points, meta?.pauses.orEmpty(), weightAt(startMs))
                if (points.size < 2 || summary.distanceM < minDistanceM) {
                    files.delete(id)
                    continue
                }
                val restored = summary.copy(title = meta?.title, startLabel = meta?.startLabel)
                files.writeMeta(id, RunJson.encodeToString(restored))
                recovered.add(restored)
            }
            return recovered
        }
    }
}
