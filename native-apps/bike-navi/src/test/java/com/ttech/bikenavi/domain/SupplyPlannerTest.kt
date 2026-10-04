package com.ttech.bikenavi.domain

import com.ttech.track.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplyPlannerTest {
    /** 直線で、距離[distM]・所要時間[durationS]のルート */
    private fun straightRoute(distM: Double, durationS: Double): Route {
        val line = Polyline(listOf(LatLon(35.0, 139.0), LatLon(35.0, 139.0 + distM / 91_000.0)))
        return Route(line, listOf(Maneuver(ManeuverKind.Arrive, null, distM, line.points.last())), distM, durationS, 0.0, 0.0, emptyList())
    }

    @Test
    fun `所要時間ぶん、一定間隔で休憩地点を提案する`() {
        // 4時間かかるルートで、1時間ごとに休憩 → 3か所(4か所目は目的地に近すぎるので出さない)
        val route = straightRoute(distM = 72_000.0, durationS = 4 * 3600.0)
        val points = SupplyPlanner.restPoints(route, departMs = 0L, intervalHours = 1.0)
        assertEquals(3, points.size)
        assertTrue(points[0].atM < points[1].atM)
        assertTrue(points[1].atM < points[2].atM)
    }

    @Test
    fun `短いルート(間隔より短い)では、休憩地点を提案しない`() {
        val route = straightRoute(distM = 10_000.0, durationS = 30 * 60.0) // 30分
        val points = SupplyPlanner.restPoints(route, departMs = 0L, intervalHours = 1.5)
        assertTrue(points.isEmpty())
    }

    @Test
    fun `最大件数を超えない`() {
        val route = straightRoute(distM = 500_000.0, durationS = 20 * 3600.0)
        val points = SupplyPlanner.restPoints(route, departMs = 0L, intervalHours = 1.0, maxPoints = 6)
        assertTrue(points.size <= 6)
    }
}
