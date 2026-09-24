package com.ttech.driverecord.domain

import com.ttech.track.data.TrackFiles
import com.ttech.track.data.TrackWriter
import com.ttech.track.domain.FixDecision
import com.ttech.track.domain.RejectKind
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
    /** 連続して「位置が飛んだ」と除いた点(互いに整合しているもの)。最初の点が古い・誤りだったときの見直し用 */
    private val jumpStreak = ArrayList<TrackPoint>()

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
        when (val decision = filter.decide(last, p)) {
            is FixDecision.Reject -> {
                rejected++
                if (decision.kind == RejectKind.JUMP && registerJump(p)) return true
                return false
            }
            FixDecision.Accept -> jumpStreak.clear()
        }
        accept(p)
        return true
    }

    /**
     * 連続して「飛んだ」と除いた点が、互いに整合していて、記録がまだ短いときは、最初の点のほうが誤り
     * (古い位置・測位開始直後の誤差)だったとみなして、いまの位置から記録し直す。
     * 記録が長いときの飛びは、本当の誤測位として除き続ける。
     */
    private fun registerJump(p: TrackPoint): Boolean {
        val previous = jumpStreak.lastOrNull()
        if (previous == null || filter.decide(previous, p) != FixDecision.Accept) jumpStreak.clear()
        jumpStreak.add(p)
        if (jumpStreak.size < REANCHOR_COUNT || points.size >= REANCHOR_MAX_POINTS) return false
        val fresh = jumpStreak.toList()
        jumpStreak.clear()
        writer?.close()
        files.csvFile(id!!).delete()
        writer = files.openWriter(id!!)
        points.clear()
        last = null
        distance = 0.0
        maxSpeed = 0.0
        fresh.forEach(::accept)
        return true
    }

    private fun accept(p: TrackPoint) {
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
        val recorded = points.toList()
        reset()

        // 出発前・到着後の停止は取り除き、ファイルも書き直す(所要時間・平均速度に混ざらないように)
        val all = IdleTrim.trim(recorded)
        if (all.size != recorded.size) rewrite(currentId, all)

        val stats = DriveStatsCalculator.compute(all)
        if (all.size < 2 || stats.distanceM < minDistanceM) {
            files.delete(currentId)
            return null
        }
        val summary = DriveSummary.from(currentId, stats, all, trigger, finished = true)
        files.writeMeta(currentId, DriveJson.encodeToString(summary))
        return summary
    }

    private fun rewrite(id: String, list: List<TrackPoint>) {
        files.csvFile(id).delete()
        files.openWriter(id).use { w -> list.forEach(w::append) }
    }

    private fun reset() {
        points.clear()
        last = null
        rejected = 0
        distance = 0.0
        maxSpeed = 0.0
        jumpStreak.clear()
    }

    companion object {
        /** この数だけ連続して整合する点が「飛んだ」扱いになったら、基準を取り直す */
        const val REANCHOR_COUNT = 3

        /** 記録した点がこれ未満のときだけ、基準を取り直す */
        const val REANCHOR_MAX_POINTS = 60

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
                val points = IdleTrim.trim(raw)
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

/**
 * 出発前・到着後の、止まっている時間を取り除く。
 * 車のエンジンを切ってから Android Auto が切れるまで(または、記録を始めてから走り出すまで)の停止が、
 * 所要時間や平均速度に混ざらないようにするため。走り出す・止まる前後の数秒は残す。
 * 一度も動いていない記録は、そのまま返す(短いドライブとして捨てられる)。
 */
object IdleTrim {
    /** これ以上の速度(m/s)で動いていたら「走っている」 */
    private const val MOVING_MPS = 1.5

    fun trim(points: List<TrackPoint>, keepMs: Long = 5_000): List<TrackPoint> {
        if (points.size < 2) return points
        val speeds = DriveStatsCalculator.pointSpeeds(points)
        val first = speeds.indexOfFirst { it >= MOVING_MPS }
        if (first < 0) return points
        val last = speeds.indexOfLast { it >= MOVING_MPS }
        val from = points[first].timeMs - keepMs
        val to = points[last].timeMs + keepMs
        val trimmed = points.filter { it.timeMs in from..to }
        return if (trimmed.size < 2) points else trimmed
    }
}
