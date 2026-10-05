package com.ttech.navi.domain

import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.LatLon
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val NaviJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** 画面にそのまま出せる文言を持つ例外(通信の失敗・ルートが無い、など) */
class NaviException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 目的地などの場所 */
@Serializable
data class Place(val name: String, val detail: String = "", val lat: Double, val lon: Double) {
    val latLon: LatLon get() = LatLon(lat, lon)
}

/** 端末が返した位置のうち、案内に使う分だけ */
data class Fix(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Double? = null,
    val bearing: Double? = null,
    val accuracyM: Double? = null,
) {
    val latLon: LatLon get() = LatLon(lat, lon)
}

/** 案内する動作の種類 */
enum class ManeuverKind { Turn, Fork, OnRamp, OffRamp, Roundabout, Arrive }

/** 案内する曲がり角・分岐・目的地など。[atM] はルートの出発地からの道のり(m)。[modifier] は OSRM の向き(left / slight right など) */
data class Maneuver(
    val kind: ManeuverKind,
    val modifier: String?,
    val atM: Double,
    val roadName: String,
    val location: LatLon,
)

/** ルート上の位置を求めた結果。[distM] はルートからの距離、[progressM] は出発地からの道のり */
data class Projection(val seg: Int, val t: Double, val distM: Double, val progressM: Double)

/** ルートの折れ線。出発地からの道のり(累積距離)を持ち、任意の位置を「ルート上のどこか」に当てはめる */
class Polyline(val points: List<LatLon>) {
    val cumM = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + GeoMath.distanceMeters(points[i - 1], points[i])
    }
    val lengthM: Double get() = if (cumM.isEmpty()) 0.0 else cumM.last()
    val segmentCount: Int get() = (points.size - 1).coerceAtLeast(0)

    /** [from]〜[to] 番目の線分のうち、[p] にいちばん近い点を探す */
    fun project(p: LatLon, from: Int = 0, to: Int = segmentCount - 1): Projection {
        require(segmentCount > 0) { "ルートに点が足りません" }
        val kx = cos(Math.toRadians(p.lat)) * METERS_PER_DEG
        var best = Projection(from, 0.0, Double.MAX_VALUE, cumM[from])
        for (i in from.coerceAtLeast(0)..to.coerceAtMost(segmentCount - 1)) {
            val ax = (points[i].lon - p.lon) * kx
            val ay = (points[i].lat - p.lat) * METERS_PER_DEG
            val bx = (points[i + 1].lon - p.lon) * kx
            val by = (points[i + 1].lat - p.lat) * METERS_PER_DEG
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-9) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < best.distM) best = Projection(i, t, d, cumM[i] + t * (cumM[i + 1] - cumM[i]))
        }
        return best
    }

    /** 道のり [progressM] にあたる線分の番号 */
    fun segmentAt(progressM: Double): Int {
        if (segmentCount == 0) return 0
        var lo = 0
        var hi = segmentCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cumM[mid] <= progressM) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun pointAt(progressM: Double): LatLon {
        if (points.isEmpty()) return LatLon(0.0, 0.0)
        if (progressM <= 0.0) return points.first()
        if (progressM >= lengthM) return points.last()
        val i = segmentAt(progressM)
        val segLen = cumM[i + 1] - cumM[i]
        val t = if (segLen < 1e-9) 0.0 else (progressM - cumM[i]) / segLen
        return LatLon(points[i].lat + (points[i + 1].lat - points[i].lat) * t, points[i].lon + (points[i + 1].lon - points[i].lon) * t)
    }

    /** [fromM]〜[toM] の折れ線(地図に描く、これから走る部分など) */
    fun slice(fromM: Double, toM: Double = lengthM): List<LatLon> {
        if (points.isEmpty()) return emptyList()
        val start = fromM.coerceIn(0.0, lengthM)
        val end = toM.coerceIn(start, lengthM)
        val out = ArrayList<LatLon>()
        out.add(pointAt(start))
        if (segmentCount > 0) {
            for (i in segmentAt(start) + 1..segmentAt(end)) if (cumM[i] > start && cumM[i] < end) out.add(points[i])
        }
        out.add(pointAt(end))
        return out
    }

    companion object {
        /** 緯度1度あたりのメートル(GeoMath と同じ地球の半径から) */
        const val METERS_PER_DEG = Math.PI / 180.0 * GeoMath.EARTH_RADIUS_M
    }
}

/**
 * 検索して得たルート。[maneuvers] は案内する動作(曲がり角など)を出発地から近い順に並べたもの。
 * 所要時間は、OSRM が道路ごとに見積もった時間を、道のりに応じて按分して求める。
 */
class Route(
    val line: Polyline,
    val maneuvers: List<Maneuver>,
    private val stepStartsM: DoubleArray,
    private val stepDurationsS: DoubleArray,
    val distanceM: Double,
    val durationS: Double,
    /** ルートのうち、有料の高速道路・自動車専用道路(の目安)を通る距離(m)。通らなければ0 */
    val tollDistanceM: Double = 0.0,
    /** 高速道路・有料道路を使わないように求めた経路(一般道のみ) */
    val tollFree: Boolean = false,
) {
    /** 出発してから、道のり [progressM] に着くまでの見積もり時間(秒) */
    fun timeAtM(progressM: Double): Double {
        if (stepStartsM.isEmpty()) return if (distanceM > 0) durationS * (progressM / distanceM).coerceIn(0.0, 1.0) else 0.0
        val p = progressM.coerceIn(0.0, distanceM)
        var i = 0
        var elapsed = 0.0
        while (i < stepStartsM.size) {
            val start = stepStartsM[i]
            val end = if (i + 1 < stepStartsM.size) stepStartsM[i + 1] else distanceM
            val len = end - start
            if (p >= end && i + 1 < stepStartsM.size) {
                elapsed += stepDurationsS[i]
                i++
                continue
            }
            val frac = if (len < 1e-9) 1.0 else ((p - start) / len).coerceIn(0.0, 1.0)
            return elapsed + stepDurationsS[i] * frac
        }
        return durationS
    }

    fun remainingS(progressM: Double): Double = (durationS - timeAtM(progressM)).coerceAtLeast(0.0)
    fun remainingM(progressM: Double): Double = (distanceM - progressM).coerceAtLeast(0.0)

    val destination: LatLon get() = line.points.last()
    val origin: LatLon get() = line.points.first()
}
