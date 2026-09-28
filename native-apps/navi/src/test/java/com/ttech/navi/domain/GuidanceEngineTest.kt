package com.ttech.navi.domain

import com.ttech.track.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceEngineTest {
    private fun runAll(route: Route, speed: Double = 10.0): Pair<List<Announcement>, List<GuidanceUpdate>> {
        val engine = GuidanceEngine(route)
        val spoken = ArrayList<Announcement>()
        val updates = ArrayList<GuidanceUpdate>()
        for (fix in Fixtures.drive(route, speed)) {
            val u = engine.update(fix)
            spoken += u.announcements
            updates += u
        }
        return spoken to updates
    }

    @Test
    fun `最初の右折の案内に、そのすぐ先の左折が添えられる`() {
        val (spoken, _) = runAll(Fixtures.shortRoute())
        assertEquals("100メートル先、右折です。その後すぐ、左折です。", spoken.first().text)
        assertEquals(0, spoken.first().maneuverIndex)
    }

    @Test
    fun `曲がり角の手前で、遠い順に案内し、もう一つ先の曲がる方向を添える`() {
        val route = Fixtures.shortRoute()
        val (spoken, _) = runAll(route)
        val third = spoken.filter { it.maneuverIndex == 2 }.map { it.text }
        // 3つ目の動作(中央通りの左折)。遠くで1回と、まもなくで1回(その間の300mは、直前の案内から近いので言わない)
        assertEquals(2, third.size)
        assertTrue(third[0], third[0].endsWith("先、左折です。その後、200メートル先、左折です。"))
        assertEquals("まもなく、左折です。その後、200メートル先、左折です。", third[1])
    }

    @Test
    fun `ご要望の形は、200メートル先で右折、その後300メートル先で左折、という案内になる`() {
        val north = 1000.0 / Polyline.METERS_PER_DEG
        val kLon = Polyline.METERS_PER_DEG * Math.cos(Math.toRadians(35.0))
        val a = LatLon(35.0, 139.0)
        val corner1 = LatLon(35.0 + 200.0 / Polyline.METERS_PER_DEG, 139.0)
        val corner2 = LatLon(corner1.lat, 139.0 + 300.0 / kLon)
        val end = LatLon(corner2.lat + north, corner2.lon)
        val line = Polyline(listOf(a, corner1, corner2, end))
        val maneuvers = listOf(
            Maneuver(ManeuverKind.Turn, "right", 200.0, "", corner1),
            Maneuver(ManeuverKind.Turn, "left", 500.0, "", corner2),
            Maneuver(ManeuverKind.Arrive, null, line.lengthM, "", end),
        )
        val route = Route(line, maneuvers, doubleArrayOf(0.0), doubleArrayOf(300.0), line.lengthM, 300.0)
        val (spoken, _) = runAll(route)
        assertEquals("200メートル先、右折です。その後、300メートル先、左折です。", spoken.first().text)
    }

    @Test
    fun `丸めると同じ言い方になる距離では、同じ案内を続けて言わない`() {
        // 北へ1230m進んで右折、そこから北へ500m
        val a = LatLon(35.0, 139.0)
        val corner = LatLon(35.0 + 1230.0 / Polyline.METERS_PER_DEG, 139.0)
        val kLon = Polyline.METERS_PER_DEG * Math.cos(Math.toRadians(35.0))
        val end = LatLon(corner.lat, 139.0 + 500.0 / kLon)
        val line = Polyline(listOf(a, corner, end))
        val maneuvers = listOf(
            Maneuver(ManeuverKind.Turn, "right", 1230.0, "", corner),
            Maneuver(ManeuverKind.Arrive, null, line.lengthM, "", end),
        )
        val route = Route(line, maneuvers, doubleArrayOf(0.0), doubleArrayOf(200.0), line.lengthM, 200.0)
        val (spoken, _) = runAll(route)
        val texts = spoken.filter { it.maneuverIndex == 0 }.map { it.text }
        // 最初(1230m)に「1キロ先」、次は300m、そして「まもなく」。1000mを切ったところで、もう一度「1キロ先」とは言わない
        assertEquals(texts.toString(), 1, texts.count { it.startsWith("1キロ先") })
        assertTrue(texts.toString(), texts.any { it.startsWith("300メートル先") })
        assertTrue(texts.toString(), texts.last().startsWith("まもなく"))
    }

    @Test
    fun `速く走っているときは、手前から「まもなく」と言う`() {
        val route = Fixtures.shortRoute()
        val engine = GuidanceEngine(route)
        val second = route.maneuvers[2] // 3つ目の動作(519m付近)
        // 130m手前を時速90km(25m/秒)で走っている。近い段階の距離は 25*7=175m なので、この時点で「まもなく」を言う
        var text: String? = null
        for (fix in Fixtures.drive(route, speedMps = 25.0, fromM = 0.0, toM = second.atM - 100.0)) {
            engine.update(fix).announcements.firstOrNull { it.maneuverIndex == 2 }?.let { text = it.text }
        }
        assertNotNull(text)
        assertTrue(text, text!!.startsWith("まもなく、左折です。"))
    }

    @Test
    fun `次の次が遠い目的地のときは、その後の案内を添えない`() {
        val route = Fixtures.shortRoute()
        val (spoken, _) = runAll(route)
        val fork = route.maneuvers.indexOfFirst { it.kind == ManeuverKind.Fork }
        val forkTexts = spoken.filter { it.maneuverIndex == fork }.map { it.text }
        assertTrue(forkTexts.isNotEmpty())
        // 分岐の先の目的地まで1.8km。「その後、2キロ先、目的地です」とは言わない
        assertTrue(forkTexts.toString(), forkTexts.none { it.contains("目的地") })
        // 分岐そのものの案内は、そのまま言う
        assertTrue(forkTexts.toString(), forkTexts.any { it.contains("分岐、左方向です。") })
    }

    @Test
    fun `目的地に着いたら、着いたと1回だけ知らせる`() {
        val route = Fixtures.shortRoute()
        val (_, updates) = runAll(route)
        assertEquals(1, updates.count { it.arrived })
        val arrivedAt = updates.first { it.arrived }
        assertTrue("残り ${arrivedAt.remainingM}", arrivedAt.remainingM <= 35.0)
        // 目的地は最後に案内される
        val (spoken, _) = runAll(route)
        assertTrue(spoken.any { it.text.contains("目的地です") })
    }

    @Test
    fun `進んだ道のりは減らず、残りの距離と時間は減っていく`() {
        val route = Fixtures.shortRoute()
        val (_, updates) = runAll(route)
        for (i in 1 until updates.size) {
            assertTrue(updates[i].progressM >= updates[i - 1].progressM)
            assertTrue(updates[i].remainingM <= updates[i - 1].remainingM + 1e-6)
        }
        assertEquals(route.distanceM, updates.first().remainingM, 15.0)
    }

    @Test
    fun `ルートから大きくはずれたら、数秒続いたあとで、はずれたと判断し、古い案内はやめる`() {
        val route = Fixtures.shortRoute()
        val engine = GuidanceEngine(route)
        val onRoute = Fixtures.drive(route, 10.0, fromM = 0.0, toM = 400.0)
        onRoute.forEach { engine.update(it) }
        val last = onRoute.last()
        val flags = ArrayList<Boolean>()
        val spokenWhileOff = ArrayList<Announcement>()
        for (k in 1..7) {
            // 北へ600mはなれた場所(ルートの近くではない)
            val off = last.copy(timeMs = last.timeMs + k * 1000L, lat = last.lat + 600.0 / Polyline.METERS_PER_DEG)
            val u = engine.update(off)
            flags += u.offRoute
            spokenWhileOff += u.announcements
        }
        assertEquals(listOf(false, false, false, false, true, true, true), flags)
        assertTrue(spokenWhileOff.isEmpty())
    }

    @Test
    fun `一瞬はずれても、ルートに戻れば、はずれたことにならない`() {
        val route = Fixtures.shortRoute()
        val engine = GuidanceEngine(route)
        val fixes = Fixtures.drive(route, 10.0, fromM = 0.0, toM = 300.0)
        fixes.forEach { engine.update(it) }
        val last = fixes.last()
        for (k in 1..3) engine.update(last.copy(timeMs = last.timeMs + k * 1000L, lat = last.lat + 600.0 / Polyline.METERS_PER_DEG))
        val back = engine.update(last.copy(timeMs = last.timeMs + 4000L))
        assertFalse(back.offRoute)
        assertEquals(0.0, back.offsetM, 1.0)
    }

    @Test
    fun `目的地の近くで止まっていれば、着いたとみなす`() {
        val route = Fixtures.shortRoute()
        val engine = GuidanceEngine(route)
        val near = route.line.pointAt(route.distanceM - 60.0)
        var arrivedCount = 0
        for (k in 0 until 10) {
            val u = engine.update(Fix(Fixtures.START_MS + k * 1000L, near.lat, near.lon, speedMps = 0.0, accuracyM = 5.0))
            if (u.arrived) arrivedCount++
        }
        assertEquals(1, arrivedCount)
    }
}
