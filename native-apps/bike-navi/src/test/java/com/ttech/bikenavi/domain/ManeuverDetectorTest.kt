package com.ttech.bikenavi.domain

import com.ttech.track.domain.LatLon
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverDetectorTest {
    private val base = LatLon(35.0, 139.0)

    /** [base] から、北に[north]m・東に[east]m 動かした点 */
    private fun offset(north: Double, east: Double): LatLon {
        val dLat = north / 111_320.0
        val dLon = east / (111_320.0 * cos(Math.toRadians(base.lat)))
        return LatLon(base.lat + dLat, base.lon + dLon)
    }

    @Test
    fun `直進だけのルートは、曲がり角が1つも無い(到着のみ)`() {
        val points = (0..10).map { offset(it * 50.0, 0.0) } // まっすぐ北に500m
        val maneuvers = ManeuverDetector.detect(Polyline(points))
        assertEquals(listOf(ManeuverKind.Arrive), maneuvers.map { it.kind })
    }

    @Test
    fun `きれいな90度の右折を検出する`() {
        val points = listOf(0.0, 100.0, 200.0, 300.0).map { offset(it, 0.0) } +
            listOf(100.0, 200.0, 300.0).map { offset(300.0, it) }
        val maneuvers = ManeuverDetector.detect(Polyline(points))
        val turns = maneuvers.filter { it.kind == ManeuverKind.Turn }
        assertEquals(1, turns.size)
        assertEquals("right", turns[0].modifier)
    }

    @Test
    fun `きれいな90度の左折を検出する`() {
        val points = listOf(0.0, 100.0, 200.0, 300.0).map { offset(it, 0.0) } +
            listOf(100.0, 200.0, 300.0).map { offset(300.0, -it) }
        val maneuvers = ManeuverDetector.detect(Polyline(points))
        val turns = maneuvers.filter { it.kind == ManeuverKind.Turn }
        assertEquals(1, turns.size)
        assertEquals("left", turns[0].modifier)
    }

    @Test
    fun `細かく続くカーブは、1つの曲がり角にまとめる`() {
        // 300m直進したあと、4mごとに8度ずつ、5回右に曲がる(合計40度・20m)
        val straight = listOf(0.0, 100.0, 200.0, 300.0).map { offset(it, 0.0) }
        val curve = ArrayList<LatLon>()
        var heading = 0.0
        var north = 300.0
        var east = 0.0
        repeat(5) {
            heading += 8.0
            north += 4.0 * cos(Math.toRadians(heading))
            east += 4.0 * kotlin.math.sin(Math.toRadians(heading))
            curve.add(offset(north, east))
        }
        val tail = (1..3).map { offset(north + it * 100.0 * cos(Math.toRadians(heading)), east + it * 100.0 * kotlin.math.sin(Math.toRadians(heading))) }
        val maneuvers = ManeuverDetector.detect(Polyline(straight + curve + tail))
        val turns = maneuvers.filter { it.kind == ManeuverKind.Turn }
        assertEquals("細かいカーブが別々の曲がり角として出てしまっている: $turns", 1, turns.size)
        assertTrue("右方向のはずが: ${turns[0].modifier}", turns[0].modifier?.contains("right") == true)
    }

    @Test
    fun `大きく向きを変えるとUターン扱いになる`() {
        val points = listOf(0.0, 100.0, 200.0, 300.0).map { offset(it, 0.0) } +
            listOf(100.0, 200.0, 300.0).map { offset(300.0 - it, 5.0) } // ほぼ真後ろに戻る
        val maneuvers = ManeuverDetector.detect(Polyline(points))
        val turns = maneuvers.filter { it.kind == ManeuverKind.Turn }
        assertEquals(1, turns.size)
        assertEquals("uturn", turns[0].modifier)
    }

    @Test
    fun `出発・到着のごく近くの向きの変化は、曲がり角にしない`() {
        // 出発して5mで右に曲がる(短すぎる)
        val points = listOf(0.0, 2.5, 5.0).map { offset(it, 0.0) } +
            listOf(5.0, 10.0, 100.0).map { offset(5.0, it - 5.0) }
        val maneuvers = ManeuverDetector.detect(Polyline(points))
        assertTrue("短すぎる曲がり角を拾ってしまっている: $maneuvers", maneuvers.none { it.kind == ManeuverKind.Turn })
    }

    @Test
    fun `右折先が広い道路のときだけ、二段階右折の目安が付く`() {
        val points = listOf(0.0, 100.0, 200.0, 300.0).map { offset(it, 0.0) } +
            listOf(100.0, 200.0, 300.0).map { offset(300.0, it) }
        val onPrimary = ManeuverDetector.detect(Polyline(points)) { "highway=primary" }
        val onResidential = ManeuverDetector.detect(Polyline(points)) { "highway=residential" }
        assertTrue(onPrimary.first { it.kind == ManeuverKind.Turn }.twoStageRightTurn)
        assertFalse(onResidential.first { it.kind == ManeuverKind.Turn }.twoStageRightTurn)
    }

    @Test
    fun `isTwoStageRightTurnRoad は、広い道路の種類か、車線2つ以上のときにtrue`() {
        assertTrue(ManeuverDetector.isTwoStageRightTurnRoad("highway=primary surface=asphalt"))
        assertTrue(ManeuverDetector.isTwoStageRightTurnRoad("highway=residential lanes=2"))
        assertFalse(ManeuverDetector.isTwoStageRightTurnRoad("highway=residential"))
        assertFalse(ManeuverDetector.isTwoStageRightTurnRoad(null))
    }
}
