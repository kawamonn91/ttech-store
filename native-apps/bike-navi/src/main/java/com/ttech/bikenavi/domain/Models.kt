package com.ttech.bikenavi.domain

import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.LatLon
import kotlin.math.cos
import kotlin.math.hypot

val BikeJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** 画面にそのまま出せる文言を持つ例外(通信の失敗・ルートが無い、など) */
class BikeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 目的地などの場所 */
@kotlinx.serialization.Serializable
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

/** 案内する動作の種類。自転車ルートは分岐・ロータリーの区別が付けられないため、曲がり角(Turn)と到着(Arrive)だけ */
enum class ManeuverKind { Turn, Arrive }

/** 自転車の走り方(BRouterの内蔵プロファイル)。公開サーバーで使えるものだけを選んでいる */
enum class RouteStyle(val profile: String, val label: String) {
    Trekking("trekking", "バランス"),
    FastBike("fastbike", "速さ重視"),
    Safety("safety", "安全重視"),
}

/**
 * 案内する曲がり角・目的地。[atM] はルートの出発地からの道のり(m)。
 * [modifier] は "left" / "slight left" / "sharp left" / "right" / "slight right" / "sharp right" / "uturn" / null(直進・到着)
 * [twoStageRightTurn] は、右折先が広い道路で、二段階右折の対象になりそうなときの目安(法規上の判定を保証するものではない)
 */
data class Maneuver(
    val kind: ManeuverKind,
    val modifier: String?,
    val atM: Double,
    val location: LatLon,
    val twoStageRightTurn: Boolean = false,
)

/** 標高の1点(ルートの出発地からの道のりと、標高) */
data class ElevationPoint(val atM: Double, val eleM: Double)

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
        const val METERS_PER_DEG = Math.PI / 180.0 * GeoMath.EARTH_RADIUS_M
    }
}

/**
 * 検索して得た自転車ルート。[maneuvers] は案内する曲がり角を出発地から近い順に並べたもの。
 * [elevation] は、出発地からの道のりごとの標高(勾配グラフに使う)。
 */
class Route(
    val line: Polyline,
    val maneuvers: List<Maneuver>,
    val distanceM: Double,
    val durationS: Double,
    /** 登り(フィルタ済み)・下りの合計(m) */
    val ascendM: Double,
    val descendM: Double,
    val elevation: List<ElevationPoint>,
) {
    fun remainingM(progressM: Double): Double = (distanceM - progressM).coerceAtLeast(0.0)

    /** 道のり[progressM]地点の、出発からの所要時間の見積もり(全体の所要時間を道のりで按分) */
    fun timeAtM(progressM: Double): Double = if (distanceM > 0) durationS * (progressM / distanceM).coerceIn(0.0, 1.0) else 0.0
    fun remainingS(progressM: Double): Double = (durationS - timeAtM(progressM)).coerceAtLeast(0.0)

    val destination: LatLon get() = line.points.last()
    val origin: LatLon get() = line.points.first()
}
