package com.ttech.bikenavi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasesTest {
    @Test
    fun `距離の言い方`() {
        assertEquals("50メートル", Phrases.distance(50.0))
        assertEquals("300メートル", Phrases.distance(320.0))
        assertEquals("1キロ", Phrases.distance(1_000.0))
    }

    @Test
    fun `所要時間の言い方`() {
        assertEquals("1分未満", Phrases.duration(20.0))
        assertEquals("45分", Phrases.duration(45.0 * 60))
        assertEquals("2時間9分", Phrases.duration(7759.6))
    }

    @Test
    fun `動作の言い方`() {
        val m = Maneuver(ManeuverKind.Turn, "right", 100.0, com.ttech.track.domain.LatLon(35.0, 139.0))
        assertEquals("右折", Phrases.maneuver(m))
        assertEquals("目的地", Phrases.maneuver(Maneuver(ManeuverKind.Arrive, null, 100.0, com.ttech.track.domain.LatLon(35.0, 139.0))))
    }

    @Test
    fun `二段階右折の目安があるときは、案内に添える`() {
        val m = Maneuver(ManeuverKind.Turn, "right", 100.0, com.ttech.track.domain.LatLon(35.0, 139.0), twoStageRightTurn = true)
        val text = Phrases.announce(200.0, m, near = false)
        assertTrue(text.contains("二段階右折"))
        assertTrue(text.contains("200メートル先、右折です"))
    }

    @Test
    fun `二段階右折の目安が無ければ、添えない`() {
        val m = Maneuver(ManeuverKind.Turn, "right", 100.0, com.ttech.track.domain.LatLon(35.0, 139.0))
        assertEquals("200メートル先、右折です。", Phrases.announce(200.0, m, near = false))
    }
}
