package com.ttech.track.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** 緯度経度の範囲。ルート全体が収まる地図の範囲を決めるのに使う */
data class GeoBounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    val center: LatLon get() = LatLon((minLat + maxLat) / 2, (minLon + maxLon) / 2)

    companion object {
        fun of(points: List<LatLon>): GeoBounds? {
            if (points.isEmpty()) return null
            var minLat = points[0].lat
            var maxLat = minLat
            var minLon = points[0].lon
            var maxLon = minLon
            for (p in points) {
                minLat = min(minLat, p.lat)
                maxLat = max(maxLat, p.lat)
                minLon = min(minLon, p.lon)
                maxLon = max(maxLon, p.lon)
            }
            return GeoBounds(minLat, minLon, maxLat, maxLon)
        }
    }
}

object GeoMath {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** 2点間の距離(m)。ハーヴァーサイン式(近距離でも精度が落ちにくい) */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    fun distanceMeters(a: LatLon, b: LatLon): Double = distanceMeters(a.lat, a.lon, b.lat, b.lon)

    /** a から b へ向かう方位(度、北=0、時計回り、0以上360未満) */
    fun bearingDegrees(a: LatLon, b: LatLon): Double {
        val phi1 = Math.toRadians(a.lat)
        val phi2 = Math.toRadians(b.lat)
        val dLambda = Math.toRadians(b.lon - a.lon)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 2つの方位(度)の差(-180〜180)。0度をまたぐ場合も正しく扱う(例: 350→10 は +20) */
    fun angleDiffDegrees(from: Double, to: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    fun absAngleDiffDegrees(from: Double, to: Double): Double = abs(angleDiffDegrees(from, to))
}
