package com.ttech.track.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 標高の上り・下りの合計と、最低・最高 */
class ElevationProfile(val gain: Double, val loss: Double, val min: Double?, val max: Double?)

object Elevation {
    /**
     * 各点の標高を、5点の移動平均でならしたもの。標高の記録が1点も無ければ null。
     * 標高が取れなかった点は、前後の値からならすので、点の数はもとの点と同じになる。
     */
    fun smoothed(points: List<TrackPoint>): DoubleArray? {
        val alts = points.map { it.altitude }
        if (alts.none { it != null }) return null
        // 標高が取れなかった点は、直前の値(先頭なら最初に取れた値)で埋める
        var last = alts.first { it != null }!!
        val filled = DoubleArray(alts.size) { i -> alts[i]?.also { last = it } ?: last }
        return DoubleArray(filled.size) { i ->
            var sum = 0.0
            var count = 0
            for (j in max(0, i - 2)..min(filled.lastIndex, i + 2)) {
                sum += filled[j]
                count++
            }
            sum / count
        }
    }

    /** 標高の上り下り。GPSの標高は数mずれるので、ならした値が3m以上動いたときだけ数える */
    fun profile(points: List<TrackPoint>): ElevationProfile {
        val smoothed = smoothed(points) ?: return ElevationProfile(0.0, 0.0, null, null)
        var ref = smoothed.first()
        var gain = 0.0
        var loss = 0.0
        for (a in smoothed) {
            val diff = a - ref
            if (abs(diff) >= THRESHOLD_M) {
                if (diff > 0) gain += diff else loss += -diff
                ref = a
            }
        }
        return ElevationProfile(gain, loss, smoothed.min(), smoothed.max())
    }

    private const val THRESHOLD_M = 3.0
}
