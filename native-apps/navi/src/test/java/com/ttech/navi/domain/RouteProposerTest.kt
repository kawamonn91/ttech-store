package com.ttech.navi.domain

import com.ttech.track.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProposerTest {
    /** 所要時間・目的地への入り方(左折か右折か)・有料道路を使うかを決めた、テスト用の経路 */
    private fun route(durationS: Double, leftEntry: Boolean, tollFree: Boolean): Route {
        val line = Polyline(listOf(LatLon(35.0, 139.0), LatLon(35.01, 139.0)))
        val modifier = if (leftEntry) "left" else "right"
        return Route(
            line = line,
            maneuvers = listOf(Maneuver(ManeuverKind.Arrive, modifier, line.lengthM, "", line.points.last())),
            stepStartsM = DoubleArray(0),
            stepDurationsS = DoubleArray(0),
            distanceM = line.lengthM,
            durationS = durationS,
            tollFree = tollFree,
        )
    }

    @Test
    fun `最短が左折で入れる経路なら、有料道路ありの1件だけ出す`() {
        val fastest = route(durationS = 100.0, leftEntry = true, tollFree = false)
        val proposals = RouteProposer.propose(listOf(fastest, route(120.0, leftEntry = false, tollFree = false)))
        assertEquals(listOf("有料道路を使う・最短(左折で進入)"), proposals.map { it.title })
        assertSame(fastest, proposals[0].route)
    }

    @Test
    fun `最短が右折で入るなら、所要時間が1割5分以内の左折経路も併せて出す`() {
        val fastest = route(100.0, leftEntry = false, tollFree = false)
        val leftTurn = route(110.0, leftEntry = true, tollFree = false)
        val proposals = RouteProposer.propose(listOf(fastest, leftTurn))
        assertEquals(listOf("有料道路を使う・最短(右折で進入)", "有料道路を使う・左折進入優先"), proposals.map { it.title })
        assertSame(fastest, proposals[0].route)
        assertSame(leftTurn, proposals[1].route)
    }

    @Test
    fun `左折の経路が1割5分を超えて遅いなら、最短だけを出す`() {
        val fastest = route(100.0, leftEntry = false, tollFree = false)
        val slowLeft = route(130.0, leftEntry = true, tollFree = false)
        val proposals = RouteProposer.propose(listOf(fastest, slowLeft))
        assertEquals(listOf("有料道路を使う・最短(右折で進入)"), proposals.map { it.title })
    }

    @Test
    fun `有料道路を使わない経路は、それだけで最短と左折進入優先を出す`() {
        val tollRoute = route(100.0, leftEntry = true, tollFree = false)
        val freeFast = route(200.0, leftEntry = false, tollFree = true)
        val freeLeft = route(220.0, leftEntry = true, tollFree = true)
        val proposals = RouteProposer.propose(listOf(tollRoute, freeFast, freeLeft))
        assertEquals(
            listOf(
                "有料道路を使う・最短(左折で進入)",
                "有料道路を使わない・最短(右折で進入)",
                "有料道路を使わない・左折進入優先",
            ),
            proposals.map { it.title },
        )
        assertSame(freeFast, proposals[1].route)
        assertSame(freeLeft, proposals[2].route)
    }

    @Test
    fun `有料道路を使わない経路が無いときは、有料道路ありだけ出す`() {
        val proposals = RouteProposer.propose(listOf(route(100.0, leftEntry = true, tollFree = false)))
        assertEquals(1, proposals.size)
        assertTrue(proposals.all { !it.route.tollFree })
    }

    @Test
    fun `候補が無いときは、何も出さない`() {
        assertTrue(RouteProposer.propose(emptyList()).isEmpty())
    }
}
