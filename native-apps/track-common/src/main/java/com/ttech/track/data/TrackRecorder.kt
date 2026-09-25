package com.ttech.track.data

import com.ttech.track.domain.FixDecision
import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.IdleTrim
import com.ttech.track.domain.RejectKind
import com.ttech.track.domain.TrackFilter
import com.ttech.track.domain.TrackPoint

/** 記録中の状況(画面に出す用) */
data class TrackLive(
    val id: String,
    val startTimeMs: Long,
    val points: Int,
    val rejected: Int,
    val distanceM: Double,
    val speedMps: Double,
    val maxSpeedMps: Double,
    val accuracyM: Double?,
    val lastPoint: TrackPoint?,
    val paused: Boolean,
    val updatedAtMs: Long,
) {
    fun elapsedMs(nowMs: Long): Long = (nowMs - startTimeMs).coerceAtLeast(0)
}

/**
 * GPSの点を受け取って、おかしい点を除き、ファイルに追記していく記録の本体(ドライブ・ランニングで共通)。
 *  - 精度が悪い・時刻が戻る・位置が飛んだ点を除く
 *  - 最初の点が古い位置・誤りだったときは、続く整合した点で基準を取り直す
 *  - 一時停止の間は、点を記録しない
 * 記録の途中でアプリが止まっても、点はファイルに残っているので、あとで復旧できる。
 */
class TrackRecorder(
    private val files: TrackFiles,
    private val filter: TrackFilter = TrackFilter(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** ほぼ止まっている間の位置のふらつきを、距離に足さないための速度(m/s) */
    private val stationaryMps: Double = 0.5,
) {
    private var id: String? = null
    private var writer: TrackWriter? = null
    private var last: TrackPoint? = null
    private val points = ArrayList<TrackPoint>()
    private var rejected = 0
    private var distance = 0.0
    private var maxSpeed = 0.0
    private var startMs = 0L
    private var paused = false
    private val jumpStreak = ArrayList<TrackPoint>()

    val isActive: Boolean get() = id != null
    val isPaused: Boolean get() = paused
    val currentId: String? get() = id

    /** 記録を始める。すでに記録中なら、その ID をそのまま返す */
    fun start(): String {
        id?.let { return it }
        startMs = clock()
        val newId = startMs.toString()
        id = newId
        writer = files.openWriter(newId)
        return newId
    }

    fun pause() {
        if (id != null) paused = true
    }

    /** 再開すると、直前の点との比較はしない(止まっている間に動いた分を、飛びと誤らないため) */
    fun resume() {
        if (id != null && paused) {
            paused = false
            last = null
            jumpStreak.clear()
        }
    }

    /** 点を1つ受け取る。記録に加えたら true、除いた(または一時停止中)なら false */
    fun onPoint(p: TrackPoint): Boolean {
        if (id == null || paused) return false
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
     * 連続して「飛んだ」と除いた点が、互いに整合していて、記録がまだ短いときは、最初の点のほうが誤りだった
     * (古い位置・測位開始直後の誤差)とみなして、いまの位置から記録し直す。長い記録での飛びは、除き続ける。
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
            val stationary = (p.speed ?: 0.0) < stationaryMps && (prev.speed ?: 0.0) < stationaryMps && d < maxOf(3.0, p.hAcc ?: 0.0)
            if (!stationary) distance += d
        }
        p.speed?.let { if (it > maxSpeed && it < 90) maxSpeed = it }
        writer?.append(p)
        points.add(p)
        last = p
    }

    fun live(): TrackLive? {
        val currentId = id ?: return null
        val l = last
        return TrackLive(
            id = currentId, startTimeMs = startMs, points = points.size, rejected = rejected, distanceM = distance,
            speedMps = l?.speed ?: 0.0, maxSpeedMps = maxSpeed, accuracyM = l?.hAcc, lastPoint = l, paused = paused, updatedAtMs = clock(),
        )
    }

    /**
     * 記録を終える。出発前・到着後の停止を取り除き(ファイルも書き直す)、残った点を返す。
     * 記録中でなければ null。
     * @param idleMovingMps 「動いている」とみなす速度。null なら、停止の除去はしない
     */
    fun stop(idleMovingMps: Double?): Finished? {
        val currentId = id ?: return null
        writer?.close()
        writer = null
        id = null
        val recorded = points.toList()
        reset()
        val all = if (idleMovingMps != null) IdleTrim.trim(recorded, idleMovingMps) else recorded
        if (all.size != recorded.size) {
            files.csvFile(currentId).delete()
            files.openWriter(currentId).use { w -> all.forEach(w::append) }
        }
        return Finished(currentId, all)
    }

    class Finished(val id: String, val points: List<TrackPoint>)

    private fun reset() {
        points.clear()
        last = null
        rejected = 0
        distance = 0.0
        maxSpeed = 0.0
        paused = false
        jumpStreak.clear()
    }

    companion object {
        /** この数だけ連続して整合する点が「飛んだ」扱いになったら、基準を取り直す */
        const val REANCHOR_COUNT = 3

        /** 記録した点がこれ未満のときだけ、基準を取り直す */
        const val REANCHOR_MAX_POINTS = 60
    }
}
