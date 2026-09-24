package com.ttech.track.domain

/**
 * 地図に描くための、ルートの分割。
 * GPSが途切れた(トンネルなど)ところで線を切り、その間は「点線」でつなぐ。
 * 実際には測っていない区間を、測ったかのような実線で描かないため。
 */
class RouteSegments(
    /** 連続して測れた区間ごとの折れ線 */
    val solid: List<List<LatLon>>,
    /** 途切れた区間の両端(点線で描く) */
    val gaps: List<Pair<LatLon, LatLon>>,
) {
    val all: List<LatLon> get() = solid.flatten()
    val isEmpty: Boolean get() = solid.all { it.isEmpty() }

    companion object {
        val EMPTY = RouteSegments(emptyList(), emptyList())

        fun from(points: List<TrackPoint>, gapSeconds: Double = 10.0): RouteSegments {
            if (points.isEmpty()) return EMPTY
            val solid = ArrayList<List<LatLon>>()
            val gaps = ArrayList<Pair<LatLon, LatLon>>()
            var current = ArrayList<LatLon>()
            current.add(points[0].latLon)
            for (i in 1 until points.size) {
                val dt = (points[i].timeMs - points[i - 1].timeMs) / 1000.0
                if (dt > gapSeconds) {
                    solid.add(current)
                    gaps.add(points[i - 1].latLon to points[i].latLon)
                    current = ArrayList()
                }
                current.add(points[i].latLon)
            }
            solid.add(current)
            return RouteSegments(solid, gaps)
        }
    }
}
