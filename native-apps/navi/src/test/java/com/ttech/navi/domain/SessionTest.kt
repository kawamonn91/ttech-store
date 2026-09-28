package com.ttech.navi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val dest = Place("東山温泉", "会津若松市", 37.4689, 139.9660)

    @Test
    fun `走った距離と時間を数え、到着したら、まとめを1回だけ出す`() {
        val route = Fixtures.shortRoute()
        val session = NavSession(route, dest, Fixtures.START_MS)
        var arrivals = 0
        var summary: ArrivalSummary? = null
        for (fix in Fixtures.drive(route, 10.0)) {
            val u = session.onFix(fix)
            if (u.guidance.arrived) {
                arrivals++
                summary = u.arrival
            }
        }
        assertEquals(1, arrivals)
        assertNotNull(summary)
        // 6.1kmを、時速36km(10m/秒)でおよそ10分
        assertEquals(route.distanceM, summary!!.distanceM, 60.0)
        assertTrue("時間 ${summary.durationMs}", summary.durationMs in 570_000L..620_000L)
        assertTrue(summary.speech, summary.speech.startsWith("目的地に到着しました。走行距離は6."))
        assertEquals(summary, session.arrival)
    }

    @Test
    fun `止まっている間の位置のふらつきは、走った距離に足さない`() {
        val t = TripTracker(0)
        val base = Fix(0, 37.0, 139.0, speedMps = 0.1, accuracyM = 5.0)
        t.onFix(base)
        for (k in 1..60) {
            val jitter = if (k % 2 == 0) 0.00002 else -0.00002
            t.onFix(base.copy(timeMs = k * 1000L, lon = 139.0 + jitter))
        }
        assertEquals(0.0, t.distanceM, 1e-9)
        // 動き出したら、数える
        t.onFix(base.copy(timeMs = 61_000, lat = 37.0 + 100.0 / Polyline.METERS_PER_DEG, speedMps = 10.0))
        assertEquals(100.0, t.distanceM, 1.0)
    }

    @Test
    fun `測位が大きく飛んだときは、走った距離に足さない`() {
        val t = TripTracker(0)
        t.onFix(Fix(0, 37.0, 139.0, speedMps = 10.0))
        t.onFix(Fix(1000, 37.5, 139.0, speedMps = 10.0)) // 約55km飛んだ
        assertEquals(0.0, t.distanceM, 1e-9)
    }

    @Test
    fun `ルートを引き直しても、走った距離・時間・地域の記憶は続く`() {
        val route = Fixtures.shortRoute()
        val session = NavSession(route, dest, Fixtures.START_MS)
        Fixtures.drive(route, 10.0, toM = 400.0).forEach { session.onFix(it) }
        val before = session.trip.distanceM
        assertTrue(before > 300)
        session.regions.onRegion(Region(7, "福島県", "会津若松市"))
        session.reroute(route)
        assertEquals(1, session.rerouteCount)
        assertEquals(before, session.trip.distanceM, 1e-9)
        assertNull(session.onRegion(Region(7, "福島県", "会津若松市")))
        // 引き直したルートの先頭から案内が始まる
        assertEquals(0.0, session.engine.progressM, 1e-9)
    }

    @Test
    fun `設定の既定値`() {
        val s = NaviSettings()
        assertTrue(s.voice && s.weatherBriefing && s.regionAnnouncements && s.headingUp)
        assertEquals(1.0f, s.speechRate, 0f)
    }
}
