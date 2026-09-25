package com.ttech.track.domain

object Speeds {
    /** 各点の速度(m/s)。端末が返した速度を使い、無ければ前の点との位置の差から求める */
    fun pointSpeeds(points: List<TrackPoint>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in points.indices) {
            val given = points[i].speed
            out[i] = when {
                given != null && given >= 0 -> given
                i == 0 -> 0.0
                else -> {
                    val dt = (points[i].timeMs - points[i - 1].timeMs) / 1000.0
                    if (dt > 0) GeoMath.distanceMeters(points[i - 1].latLon, points[i].latLon) / dt else 0.0
                }
            }
        }
        return out
    }
}

/**
 * 出発前・到着後の、止まっている時間を取り除く。
 * 走り出す・止まる前後の数秒は残す。一度も動いていない記録は、そのまま返す。
 *
 * @param movingMps これ以上の速度(m/s)で動いていたら「動いている」。車は1.5、ランニング・歩きは0.8が目安
 */
object IdleTrim {
    fun trim(points: List<TrackPoint>, movingMps: Double = 1.5, keepMs: Long = 5_000): List<TrackPoint> {
        if (points.size < 2) return points
        val speeds = Speeds.pointSpeeds(points)
        val first = speeds.indexOfFirst { it >= movingMps }
        if (first < 0) return points
        val last = speeds.indexOfLast { it >= movingMps }
        val from = points[first].timeMs - keepMs
        val to = points[last].timeMs + keepMs
        val trimmed = points.filter { it.timeMs in from..to }
        return if (trimmed.size < 2) points else trimmed
    }
}
