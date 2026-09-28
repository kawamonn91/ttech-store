package com.ttech.navi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OsrmParserTest {
    @Test
    fun `実際の応答から、ルートと案内する動作を取り出せる`() {
        val route = Fixtures.shortRoute()
        assertEquals(6069.0, route.distanceM, 60.0)
        assertEquals(458.8, route.durationS, 0.1)
        assertTrue(route.line.points.size > 200)

        // 応答の11ステップのうち、案内するのは9つ(出発・道なり・new name は案内しない)
        val kinds = route.maneuvers.map { it.kind to it.modifier }
        assertEquals(
            listOf(
                ManeuverKind.Turn to "right",
                ManeuverKind.Turn to "left",
                ManeuverKind.Turn to "left",
                ManeuverKind.Turn to "left", // continue / left
                ManeuverKind.Turn to "right", // continue / right
                ManeuverKind.Turn to "right",
                ManeuverKind.Turn to "left", // continue / left
                ManeuverKind.Fork to "slight left",
                ManeuverKind.Arrive to "left", // 目的地は進行方向の左側
            ),
            kinds,
        )
    }

    @Test
    fun `動作の位置は出発地からの道のりで、近い順に並び、最後は目的地`() {
        val route = Fixtures.shortRoute()
        val at = route.maneuvers.map { it.atM }
        assertEquals(at.sorted(), at)
        assertEquals(105.0, at[0], 10.0) // 最初の右折は出発から約105m
        assertEquals(148.0, at[1], 10.0) // その約43m先に左折
        assertEquals(route.distanceM, at.last(), 0.001)
        assertEquals("中央通り", route.maneuvers[1].roadName)
    }

    @Test
    fun `所要時間は道のりに応じて按分される`() {
        val route = Fixtures.shortRoute()
        assertEquals(0.0, route.timeAtM(0.0), 0.001)
        assertEquals(route.durationS, route.timeAtM(route.distanceM), 1.0)
        val half = route.timeAtM(route.distanceM / 2)
        assertTrue("半分の地点の時間 $half", half > 0 && half < route.durationS)
        assertEquals(route.durationS - half, route.remainingS(route.distanceM / 2), 0.5)
    }

    @Test
    fun `案内する動作の選び方`() {
        assertNull(OsrmParser.kindOf("new name", "straight"))
        assertNull(OsrmParser.kindOf("depart", null))
        assertNull(OsrmParser.kindOf("merge", "slight right"))
        assertNull(OsrmParser.kindOf("turn", "straight"))
        assertNull(OsrmParser.kindOf("continue", "slight left")) // ゆるいカーブは案内しない
        assertEquals(ManeuverKind.Turn, OsrmParser.kindOf("continue", "uturn"))
        assertEquals(ManeuverKind.Turn, OsrmParser.kindOf("end of road", "left"))
        assertEquals(ManeuverKind.OnRamp, OsrmParser.kindOf("on ramp", "right"))
        assertEquals(ManeuverKind.Roundabout, OsrmParser.kindOf("rotary", "right"))
        assertNull(OsrmParser.kindOf("exit roundabout", "right"))
    }

    @Test
    fun `ルートが無い応答は、分かりやすい文言の例外にする`() {
        try {
            OsrmParser.parse("""{"code":"NoRoute","message":"Impossible route"}""")
            fail()
        } catch (e: NaviException) {
            assertTrue(e.message!!.contains("ルートが見つかりませんでした"))
        }
        try {
            OsrmParser.parse("これはJSONではない")
            fail()
        } catch (e: NaviException) {
            assertTrue(e.message!!.contains("読み取れませんでした"))
        }
    }
}
