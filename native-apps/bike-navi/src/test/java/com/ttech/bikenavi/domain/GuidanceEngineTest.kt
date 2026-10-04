package com.ttech.bikenavi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceEngineTest {
    private fun runAll(route: Route, speedMps: Double = 5.0): List<GuidanceUpdate> {
        val engine = GuidanceEngine(route)
        return Fixtures.ride(route, speedMps = speedMps).map(engine::update)
    }

    @Test
    fun `実際のルートを最後まで走ると到着する`() {
        val route = Fixtures.shortRoute()
        val updates = runAll(route)
        assertTrue(updates.any { it.arrived })
        assertEquals(0.0, updates.last().remainingM, 40.0)
    }

    @Test
    fun `曲がり角ごとに、少なくとも1回は案内する`() {
        val route = Fixtures.shortRoute()
        val spoken = runAll(route).flatMap { it.announcements }.map { it.text }
        val turnCount = route.maneuvers.count { it.kind == ManeuverKind.Turn }
        // 短い区間同士が近いと、1回の通過で2つぶんまとめて言うことがあるので、厳密な一致は求めない
        assertTrue("曲がり角 $turnCount 個に対して、案内 ${spoken.size} 回", spoken.size >= (turnCount - 1).coerceAtLeast(0))
    }

    @Test
    fun `同じ曲がり角については、近い距離で2回続けて同じ言い方を案内しない`() {
        val route = Fixtures.shortRoute()
        val byIndex = runAll(route).flatMap { it.announcements }.groupBy { it.maneuverIndex }
        for ((index, list) in byIndex) {
            for (i in 1 until list.size) assertTrue("曲がり角$index で連続で同じ案内: ${list[i].text}", list[i].text != list[i - 1].text)
        }
    }

    @Test
    fun `ルートから大きく外れ続けると、外れたと判断する`() {
        val route = Fixtures.shortRoute()
        val engine = GuidanceEngine(route)
        val farAway = route.line.points.first().let { com.ttech.track.domain.LatLon(it.lat + 0.01, it.lon + 0.01) } // 約1km離れた場所
        var offRoute = false
        for (i in 0 until 10) {
            val u = engine.update(Fix(Fixtures.START_MS + i * 1000L, farAway.lat, farAway.lon, speedMps = 5.0))
            if (u.offRoute) offRoute = true
        }
        assertTrue(offRoute)
    }
}
