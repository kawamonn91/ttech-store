package com.ttech.runtracker.domain

import com.ttech.track.domain.Elevation
import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.Speeds
import com.ttech.track.domain.TrackPoint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** 1kmごとの区間(最後は、1km未満の端数)。時間は、走っていた時間 */
class RunSplit(
    /** 1から数える。最後の端数の区間も、続きの番号 */
    val index: Int,
    val distanceM: Double,
    val movingMs: Long,
    /** この区間の標高の変化(上りが正)。標高が無い記録では null */
    val elevationDiffM: Double?,
) {
    val paceSecPerKm: Double get() = if (distanceM > 0) movingMs / 1000.0 / (distanceM / 1000.0) else 0.0
    val isPartial: Boolean get() = distanceM < 999.5
}

class RunEffort(val def: EffortDef, val timeMs: Long) {
    val paceSecPerKm: Double get() = timeMs / 1000.0 / (def.meters / 1000.0)
}

/** グラフ用に、点ごとに並べたデータ(点の数はすべて同じ) */
class RunSeries(
    /** 走った距離(km)。一時停止や止まっている間は増えない */
    val distKm: DoubleArray,
    /** ならしたペース(秒/km)。止まっているところは、走っている間のペースの中央値の1.4倍(遅い側の上限)になる */
    val paceSecPerKm: DoubleArray,
    val altitudeM: DoubleArray?,
)

class RunStats(
    val startTimeMs: Long,
    val endTimeMs: Long,
    /** 開始から終了まで(一時停止も含む) */
    val elapsedMs: Long,
    /** 走っていた時間。ペースはこれで求める */
    val movingMs: Long,
    val distanceM: Double,
    val elevationGainM: Double,
    val elevationLossM: Double,
    val minAltitudeM: Double?,
    val maxAltitudeM: Double?,
    val caloriesKcal: Double,
    val splits: List<RunSplit>,
    val efforts: List<RunEffort>,
    /** GPSが途切れた(トンネルなど)回数。一時停止は数えない */
    val gapCount: Int,
    val pointCount: Int,
    val avgAccuracyM: Double?,
    val series: RunSeries,
) {
    val avgPaceSecPerKm: Double get() = if (distanceM > 0 && movingMs > 0) movingMs / 1000.0 / (distanceM / 1000.0) else 0.0
    val avgSpeedMps: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) else 0.0

    /** 止まっていた・一時停止していた時間 */
    val stoppedMs: Long get() = max(0L, elapsedMs - movingMs)

    /** 区間の中で、いちばん速い区間(端数の区間は除く)。無ければ null */
    val fastestSplit: RunSplit? get() = splits.filter { !it.isPartial }.minByOrNull { it.paceSecPerKm }
}

/** 記録した点の並びから、距離・時間・ペース・1kmごとの区間・自己ベスト・消費カロリーをまとめて求める */
object RunStatsCalculator {
    /** 連続する2点の間がこれより空いていたら「GPSが途切れた」とみなす(秒) */
    const val GAP_SECONDS = 10.0

    /** これ未満の速度は「止まっている」(m/s) */
    private const val STOPPED_MPS = 0.5

    /** 走っている(歩いている)とみなす速度(m/s)。時速2.9km */
    const val MOVING_MPS = 0.8

    /** 停止中の測位のぶれ(位置が数mふらつく)で距離が積み上がらないようにするための最小値(m) */
    private const val JITTER_FLOOR_M = 3.0

    /** グラフのペースの範囲(秒/km)。これを外れる値は端に寄せる */
    const val MIN_PACE = 120.0
    const val MAX_PACE = 900.0

    /** 1km未満の端数の区間は、これ以上のときだけ出す(m) */
    private const val MIN_PARTIAL_M = 50.0

    /** ペースの平滑化の幅(前後何点ぶんまで平均するか)。1秒ごとの点なら、前後5秒 */
    private const val SMOOTH_HALF = 5

    /** グラフのペースを、走っている間の中央値の何倍までに収めるか(速い側・遅い側) */
    private const val CHART_FAST_LIMIT = 0.7
    private const val CHART_SLOW_LIMIT = 1.4

    fun compute(points: List<TrackPoint>, pauses: List<RunPause> = emptyList(), weightKg: Double = Calories.DEFAULT_WEIGHT_KG): RunStats {
        val n = points.size
        if (n == 0) return empty()
        val speeds = Speeds.pointSpeeds(points)
        val cumD = DoubleArray(n)
        val cumT = LongArray(n) // 走っていた時間(ms)の積算

        var distance = 0.0
        var moving = 0L
        var gaps = 0
        var kcal = 0.0
        for (i in 1 until n) {
            val a = points[i - 1]
            val b = points[i]
            val dtMs = b.timeMs - a.timeMs
            cumD[i] = distance
            cumT[i] = moving
            if (dtMs <= 0) continue
            // 一時停止をはさんだ区間は、動いた分も時間も数えない
            if (pauses.any { it.startMs <= b.timeMs && it.endMs >= a.timeMs }) continue
            val dtSec = dtMs / 1000.0
            val d = GeoMath.distanceMeters(a.latLon, b.latLon)
            val isGap = dtSec > GAP_SECONDS
            if (isGap) gaps++
            val jitter = !isGap && speeds[i] < STOPPED_MPS && speeds[i - 1] < STOPPED_MPS && d < max(JITTER_FLOOR_M, b.hAcc ?: 0.0)
            if (!jitter) distance += d
            val segSpeed = if (isGap) d / dtSec else (speeds[i] + speeds[i - 1]) / 2
            val isMoving = segSpeed >= MOVING_MPS && !jitter
            if (isMoving) moving += dtMs
            kcal += Calories.kcal(if (isMoving) segSpeed else 0.0, dtMs, weightKg)
            cumD[i] = distance
            cumT[i] = moving
        }

        val profile = Elevation.profile(points)
        val altitudes = Elevation.smoothed(points)
        return RunStats(
            startTimeMs = points.first().timeMs,
            endTimeMs = points.last().timeMs,
            elapsedMs = points.last().timeMs - points.first().timeMs,
            movingMs = moving,
            distanceM = distance,
            elevationGainM = profile.gain,
            elevationLossM = profile.loss,
            minAltitudeM = profile.min,
            maxAltitudeM = profile.max,
            caloriesKcal = kcal,
            splits = splits(cumD, cumT, altitudes),
            efforts = efforts(cumD, cumT),
            gapCount = gaps,
            pointCount = n,
            avgAccuracyM = points.mapNotNull { it.hAcc }.takeIf { it.isNotEmpty() }?.average(),
            series = RunSeries(
                distKm = DoubleArray(n) { cumD[it] / 1000.0 },
                paceSecPerKm = smoothedPace(speeds),
                altitudeM = altitudes,
            ),
        )
    }

    private fun empty() = RunStats(
        0, 0, 0, 0, 0.0, 0.0, 0.0, null, null, 0.0, emptyList(), emptyList(), 0, 0, null,
        RunSeries(DoubleArray(0), DoubleArray(0), null),
    )

    /** 前後の点の速度を平均してならし、ペース(秒/km)にする。グラフがギザギザにならないため */
    private fun smoothedPace(speeds: DoubleArray): DoubleArray {
        val n = speeds.size
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + speeds[i]
        val raw = DoubleArray(n) { i ->
            val from = max(0, i - SMOOTH_HALF)
            val to = min(n - 1, i + SMOOTH_HALF)
            val v = (prefix[to + 1] - prefix[from]) / (to - from + 1)
            if (v < 0.3) MAX_PACE else (1000.0 / v).coerceIn(MIN_PACE, MAX_PACE)
        }
        // 信号待ちなどで止まったところが、グラフの縦軸を押し広げて、走っているときのペースの変化が見えなくならないように、
        // 走っている間のペースの中央値を基準にして、その少し外側までに収める
        val running = raw.filter { it < MAX_PACE }.sorted()
        if (running.isEmpty()) return raw
        val median = running[running.size / 2]
        val lo = max(MIN_PACE, median * CHART_FAST_LIMIT)
        val hi = min(MAX_PACE, median * CHART_SLOW_LIMIT)
        return DoubleArray(n) { raw[it].coerceIn(lo, hi) }
    }

    /** 走った距離が1kmに達するごとに区切る。境目の時刻・標高は、前後の点のあいだを直線でつないで求める */
    private fun splits(cumD: DoubleArray, cumT: LongArray, altitudes: DoubleArray?): List<RunSplit> {
        val n = cumD.size
        val out = ArrayList<RunSplit>()
        if (n < 2) return out
        var boundary = 1
        var prevT = 0.0
        var prevAlt = altitudes?.first()
        for (i in 1 until n) {
            val d0 = cumD[i - 1]
            val d1 = cumD[i]
            while (d1 >= boundary * 1000.0 && d1 > d0) {
                val f = (boundary * 1000.0 - d0) / (d1 - d0)
                val t = cumT[i - 1] + f * (cumT[i] - cumT[i - 1])
                val alt = altitudes?.let { it[i - 1] + f * (it[i] - it[i - 1]) }
                out.add(RunSplit(boundary, 1000.0, (t - prevT).roundToLong(), if (alt != null && prevAlt != null) alt - prevAlt else null))
                prevT = t
                prevAlt = alt
                boundary++
            }
        }
        val rest = cumD[n - 1] - (boundary - 1) * 1000.0
        if (rest >= MIN_PARTIAL_M) {
            val endAlt = altitudes?.last()
            out.add(RunSplit(boundary, rest, (cumT[n - 1] - prevT).roundToLong(), if (endAlt != null && prevAlt != null) endAlt - prevAlt else null))
        }
        return out
    }

    /** 各距離を、どこから走り始めても、いちばん速く走れた時間(自己ベスト)。走った距離に満たない距離は出さない */
    private fun efforts(cumD: DoubleArray, cumT: LongArray): List<RunEffort> =
        EffortDef.entries.mapNotNull { def -> bestTime(cumD, cumT, def.meters)?.let { RunEffort(def, it) } }

    /** 窓(距離 [target])を後ろにずらしながら、かかった時間のいちばん短いものを探す */
    fun bestTime(cumD: DoubleArray, cumT: LongArray, target: Double): Long? {
        val n = cumD.size
        if (n < 2 || cumD[n - 1] < target || target <= 0) return null
        var best = Double.MAX_VALUE
        var s = 0
        for (i in 1 until n) {
            val x = cumD[i] - target
            if (x < 0) continue
            // cumD[s] <= x < cumD[s+1] になる s まで進める(窓の始まりの位置)
            while (s + 1 < n && cumD[s + 1] <= x) s++
            val d0 = cumD[s]
            val d1 = cumD[min(s + 1, n - 1)]
            val startT = if (d1 > d0) cumT[s] + (x - d0) / (d1 - d0) * (cumT[min(s + 1, n - 1)] - cumT[s]) else cumT[s].toDouble()
            val dur = cumT[i] - startT
            if (dur > 0 && dur < best) best = dur
        }
        return if (best == Double.MAX_VALUE) null else best.roundToLong()
    }
}

/**
 * 消費カロリーの推定。運動の強さ(METs)× 体重(kg)× 時間(h)× 1.05 で求める。
 * METs は、走る速さから「身体活動のコンペンディウム」の値を線でつないで求める(速さが上がるほど大きくなる)。
 * 1.05 は、日本の「健康づくりのための身体活動基準」で使われる換算(1 METs・時・kg ≒ 1.05 kcal)。
 */
object Calories {
    /** 体重を記録していないときに使う標準の体重(kg) */
    const val DEFAULT_WEIGHT_KG = 60.0

    /** 立ち止まっているときのMETs(安静よりわずかに高い) */
    private const val STANDING_MET = 1.5

    private const val KCAL_PER_MET_HOUR_KG = 1.05

    /** 時速(km/h)とMETsの対応。歩きからジョギング、ランニングまで */
    private val TABLE = listOf(
        0.0 to STANDING_MET,
        2.0 to 2.0,
        3.2 to 2.8,
        4.8 to 3.5,
        5.6 to 4.3,
        6.4 to 6.0,
        8.0 to 8.3,
        8.4 to 9.0,
        9.7 to 9.8,
        10.8 to 10.5,
        11.3 to 11.0,
        12.1 to 11.5,
        12.9 to 11.8,
        13.8 to 12.3,
        14.5 to 12.8,
        16.1 to 14.5,
        17.7 to 16.0,
        19.3 to 19.0,
        20.9 to 19.8,
        22.5 to 23.0,
    )

    fun met(speedMps: Double): Double {
        val kmh = speedMps * 3.6
        if (kmh <= TABLE.first().first) return TABLE.first().second
        if (kmh >= TABLE.last().first) return TABLE.last().second
        val i = TABLE.indexOfFirst { it.first >= kmh }
        val (x0, y0) = TABLE[i - 1]
        val (x1, y1) = TABLE[i]
        return y0 + (kmh - x0) / (x1 - x0) * (y1 - y0)
    }

    fun kcal(speedMps: Double, durationMs: Long, weightKg: Double): Double =
        met(speedMps) * KCAL_PER_MET_HOUR_KG * weightKg * (durationMs / 3_600_000.0)
}
