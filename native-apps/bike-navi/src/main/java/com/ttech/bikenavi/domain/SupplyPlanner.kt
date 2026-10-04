package com.ttech.bikenavi.domain

import com.ttech.track.domain.LatLon

/** 補給・休憩を提案する1地点(出発地からの道のりと、その場所) */
data class RestPoint(val atM: Double, val location: LatLon, val etaMs: Long)

/**
 * ペース配分(想定の所要時間)に応じて、補給・休憩をすすめる地点を決める。
 * 実際のコンビニ・飲食店などを探すのは、この地点の近くを[com.ttech.bikenavi.data.OverpassClient]で検索する側の役目。
 */
object SupplyPlanner {
    /** [intervalHours] 時間走るごとに1か所、最大 [maxPoints] か所まで。最後の手前(目的地のすぐ近く)は提案しない */
    fun restPoints(route: Route, departMs: Long, intervalHours: Double, maxPoints: Int = 6): List<RestPoint> {
        if (route.durationS <= 0 || intervalHours <= 0) return emptyList()
        val totalHours = route.durationS / 3600.0
        val n = (totalHours / intervalHours).toInt().coerceAtMost(maxPoints)
        if (n < 1) return emptyList()
        return (1..n).mapNotNull { i ->
            val targetS = i * intervalHours * 3600.0
            if (targetS >= route.durationS - MIN_GAP_FROM_END_S) return@mapNotNull null
            val ratio = (targetS / route.durationS).coerceIn(0.0, 1.0)
            val atM = route.distanceM * ratio
            RestPoint(atM, route.line.pointAt(atM), departMs + (targetS * 1000).toLong())
        }
    }

    /** 目的地まで、これより近い時点では休憩を提案しない(着く直前に提案しても意味が薄いため) */
    private const val MIN_GAP_FROM_END_S = 20.0 * 60.0
}
