package com.ttech.driverecord.domain

import com.ttech.track.domain.*

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 信号待ちなどで止まった場所。出発前・到着後の停止は含めない */
data class StopInfo(val startMs: Long, val durationMs: Long, val lat: Double, val lon: Double) {
    /** サービスエリアなどでの休憩とみなせる長さ([BREAK_MIN_SECONDS]以上)か */
    val isBreak: Boolean get() = durationMs >= BREAK_MIN_SECONDS * 1000

    companion object {
        /** これ以上の停止は、信号待ちではなく「休憩」として表示する(5分) */
        const val BREAK_MIN_SECONDS = 300.0
    }
}

enum class EventType(val label: String, val unit: String) {
    HardBrake("急ブレーキ", "m/s²"),
    HardAccel("急加速", "m/s²"),
    SharpCorner("急ハンドル(急カーブ)", "m/s²"),
}

/**
 * 運転中の目立った出来事。[value] は加速度の大きさ(m/s²、正の値)。
 * 加速度は GPS の速度・方位の変化から求めている(端末のセンサーは使わない。車内での端末の向きに左右されないため)。
 */
data class DriveEvent(
    val type: EventType,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val value: Double,
    val speedMps: Double,
)

data class DriveStats(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    /** 動いていた時間(時速3.6km以上)。信号待ちなどは含まない */
    val movingMs: Long,
    val distanceM: Double,
    /** 動いていた間の平均速度(m/s) */
    val avgMovingSpeedMps: Double,
    val maxSpeedMps: Double,
    val elevationGainM: Double,
    val elevationLossM: Double,
    val minAltitudeM: Double?,
    val maxAltitudeM: Double?,
    val stops: List<StopInfo>,
    val events: List<DriveEvent>,
    /** GPSが途切れた(トンネルなど)回数。この間は直線でつないでいる */
    val gapCount: Int,
    val pointCount: Int,
    val avgAccuracyM: Double?,
    /** 速度帯ごとの時間(ms)。境界は [SPEED_BAND_LIMITS_KMH] */
    val speedBandMs: List<Long>,
) {
    val stoppedMs: Long get() = max(0L, durationMs - movingMs)
    val avgSpeedMps: Double get() = if (durationMs > 0) distanceM / (durationMs / 1000.0) else 0.0
    /** 休憩とみなせる長さの停止(サービスエリアなど)。短い信号待ちは含まない */
    val breaks: List<StopInfo> get() = stops.filter { it.isBreak }
    val hardBrakeCount: Int get() = events.count { it.type == EventType.HardBrake }
    val hardAccelCount: Int get() = events.count { it.type == EventType.HardAccel }
    val sharpCornerCount: Int get() = events.count { it.type == EventType.SharpCorner }

    companion object {
        /** 速度帯の境界(km/h)。 ~10 / 10~30 / 30~50 / 50~80 / 80~100 / 100~ の6区分 */
        val SPEED_BAND_LIMITS_KMH = listOf(10.0, 30.0, 50.0, 80.0, 100.0)
    }
}

/** 記録した点の並びから、距離・時間・速度・標高・停止・急な操作をまとめて求める */
object DriveStatsCalculator {
    /** 連続する2点の間がこれより空いていたら「GPSが途切れた」とみなす(秒) */
    const val GAP_SECONDS = 10.0

    /** これ未満の速度は「止まっている」(m/s) */
    private const val STOPPED_MPS = 0.5
    private const val RESUME_MPS = 1.5

    /** 動いているとみなす速度(m/s)。時速3.6km */
    private const val MOVING_MPS = 1.0

    private const val HARD_BRAKE = -3.0
    private const val HARD_ACCEL = 3.0
    private const val SHARP_CORNER = 3.5
    private const val MIN_STOP_SECONDS = 20.0
    private const val EVENT_MERGE_MS = 3_000L

    /** 停止中の測位のぶれ(位置が数mふらつく)で距離が積み上がらないようにするための最小値(m) */
    private const val JITTER_FLOOR_M = 3.0

    fun compute(points: List<TrackPoint>): DriveStats {
        if (points.isEmpty()) {
            return DriveStats(0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, emptyList(), emptyList(), 0, 0, null, List(6) { 0L })
        }
        val n = points.size
        val speeds = pointSpeeds(points)
        val smoothSpeeds = medianOf3(speeds)

        var distance = 0.0
        var movingMs = 0L
        var gaps = 0
        val bands = LongArray(6)
        for (i in 1 until n) {
            val a = points[i - 1]
            val b = points[i]
            val dtMs = b.timeMs - a.timeMs
            if (dtMs <= 0) continue
            val dtSec = dtMs / 1000.0
            val d = GeoMath.distanceMeters(a.latLon, b.latLon)
            val isGap = dtSec > GAP_SECONDS
            if (isGap) gaps++

            val jitter = !isGap && speeds[i] < STOPPED_MPS && speeds[i - 1] < STOPPED_MPS && d < max(JITTER_FLOOR_M, b.hAcc ?: 0.0)
            if (!jitter) distance += d

            val segSpeed = if (isGap) d / dtSec else (speeds[i] + speeds[i - 1]) / 2
            if (segSpeed >= MOVING_MPS && !jitter) movingMs += dtMs
            bands[speedBand(segSpeed * 3.6)] += dtMs
        }

        val durationMs = points.last().timeMs - points.first().timeMs
        val avgMoving = if (movingMs > 0) distance / (movingMs / 1000.0) else 0.0
        val elevation = Elevation.profile(points)

        return DriveStats(
            startTimeMs = points.first().timeMs,
            endTimeMs = points.last().timeMs,
            durationMs = durationMs,
            movingMs = movingMs,
            distanceM = distance,
            avgMovingSpeedMps = avgMoving,
            maxSpeedMps = smoothSpeeds.maxOrNull() ?: 0.0,
            elevationGainM = elevation.gain,
            elevationLossM = elevation.loss,
            minAltitudeM = elevation.min,
            maxAltitudeM = elevation.max,
            stops = stops(points, speeds),
            events = events(points, smoothSpeeds),
            gapCount = gaps,
            pointCount = n,
            avgAccuracyM = points.mapNotNull { it.hAcc }.takeIf { it.isNotEmpty() }?.average(),
            speedBandMs = bands.toList(),
        )
    }

    fun speedBand(kmh: Double): Int {
        val limits = DriveStats.SPEED_BAND_LIMITS_KMH
        for (i in limits.indices) if (kmh < limits[i]) return i
        return limits.size
    }

    /** 各点の速度(m/s)。端末が返した速度を使い、無ければ前の点との位置の差から求める */
    fun pointSpeeds(points: List<TrackPoint>): DoubleArray = Speeds.pointSpeeds(points)

    /** 3点の中央値。速度が1点だけ跳ねる(瞬間的な誤差)のを、最高速度や加速度から除く */
    private fun medianOf3(values: DoubleArray): DoubleArray {
        if (values.size < 3) return values.copyOf()
        val out = values.copyOf()
        for (i in 1 until values.size - 1) {
            val a = values[i - 1]
            val b = values[i]
            val c = values[i + 1]
            out[i] = max(min(a, b), min(max(a, b), c))
        }
        return out
    }

    /** 信号待ちなどの停止。出発前と到着後に止まっている時間は数えない */
    private fun stops(points: List<TrackPoint>, speeds: DoubleArray): List<StopInfo> {
        val result = ArrayList<StopInfo>()
        var i = 0
        val n = points.size
        // 最初に動き出すまでは「出発前」
        while (i < n && speeds[i] < RESUME_MPS) i++
        // 最後に止まる部分は「到着後」なので、そこまでで打ち切る
        var end = n - 1
        while (end > i && speeds[end] < RESUME_MPS) end--
        while (i <= end) {
            if (speeds[i] < STOPPED_MPS) {
                val startIdx = i
                while (i <= end && speeds[i] < RESUME_MPS) i++
                val durationMs = points[min(i, n - 1)].timeMs - points[startIdx].timeMs
                if (durationMs / 1000.0 >= MIN_STOP_SECONDS) {
                    result.add(StopInfo(points[startIdx].timeMs, durationMs, points[startIdx].lat, points[startIdx].lon))
                }
            } else {
                i++
            }
        }
        return result
    }

    /** 急ブレーキ・急加速・急ハンドル。近い時刻のものは1つにまとめ、いちばん大きい値を残す */
    private fun events(points: List<TrackPoint>, speeds: DoubleArray): List<DriveEvent> {
        val raw = ArrayList<DriveEvent>()
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dt = (b.timeMs - a.timeMs) / 1000.0
            if (dt < 0.5 || dt > 2.5) continue // 1秒間隔の連続した測位だけで判断する(途切れた区間は判断しない)

            val accel = (speeds[i] - speeds[i - 1]) / dt
            if (accel <= HARD_BRAKE && speeds[i - 1] >= 3.0) {
                raw.add(DriveEvent(EventType.HardBrake, b.timeMs, b.lat, b.lon, -accel, speeds[i - 1]))
            } else if (accel >= HARD_ACCEL && speeds[i] >= 3.0) {
                raw.add(DriveEvent(EventType.HardAccel, b.timeMs, b.lat, b.lon, accel, speeds[i]))
            }

            val ba = a.bearing
            val bb = b.bearing
            if (ba != null && bb != null && speeds[i] >= 5.0 && speeds[i - 1] >= 5.0) {
                val yawRate = Math.toRadians(GeoMath.absAngleDiffDegrees(ba, bb)) / dt
                val lateral = speeds[i] * yawRate
                if (lateral >= SHARP_CORNER) {
                    raw.add(DriveEvent(EventType.SharpCorner, b.timeMs, b.lat, b.lon, lateral, speeds[i]))
                }
            }
        }
        return mergeNearby(raw)
    }

    private fun mergeNearby(events: List<DriveEvent>): List<DriveEvent> {
        val out = ArrayList<DriveEvent>()
        for (e in events) {
            val last = out.lastOrNull { it.type == e.type }
            if (last != null && e.timeMs - last.timeMs <= EVENT_MERGE_MS) {
                if (e.value > last.value) out[out.lastIndexOf(last)] = e
            } else {
                out.add(e)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
