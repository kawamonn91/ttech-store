package com.ttech.navi.domain

import com.ttech.track.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaParserTest {
    // 3点(東京・中間・名古屋付近)を精度6で符号化したもの
    private val shape = "_zx`cAwruqiG~{`JvjeIvwaSfq|dD"

    private fun response(hasHighway: Boolean) = """
        {"trip":{
          "summary":{"has_highway":$hasHighway,"has_toll":true,"time":3600.0,"length":350.0},
          "legs":[{"shape":"$shape","maneuvers":[
            {"type":3,"begin_shape_index":0,"time":10.0,"length":0.1,"street_names":[]},
            {"type":15,"begin_shape_index":1,"time":100.0,"length":50.0,"street_names":["国道20号"]},
            {"type":4,"begin_shape_index":2,"time":0.0,"length":0.0,"street_names":[]}
          ]}]
        }}
    """.trimIndent()

    @Test
    fun `高速道路を使わない応答は、一般道のみのルートとして取り出せる`() {
        val route = ValhallaParser.parseTollFree(response(hasHighway = false))!!
        assertTrue(route.tollFree)
        assertEquals(0.0, route.tollDistanceM, 0.0)
        assertEquals(3600.0, route.durationS, 0.0)
        assertEquals(3, route.line.points.size)

        // 出発の操作(type 3)は案内しない。左折と目的地が残る
        assertEquals(listOf(ManeuverKind.Turn to "left", ManeuverKind.Arrive to null), route.maneuvers.map { it.kind to it.modifier })
        // 左折は、2番目の点(道のり)から。目的地は終点
        assertEquals(route.line.cumM[1], route.maneuvers[0].atM, 1e-6)
        assertEquals(route.line.lengthM, route.maneuvers[1].atM, 1e-6)
        assertEquals("国道20号", route.maneuvers[0].roadName)
    }

    @Test
    fun `実際のValhallaの応答(東京→名古屋・有料道路を避ける指定)から、高速道路を使わないルートを取り出せる`() {
        val text = Fixtures.text("valhalla_tokyo_nagoya.json")
        val route = ValhallaParser.parseTollFree(text)!!
        assertTrue(route.tollFree)
        assertEquals(394.8, route.distanceM / 1000.0, 1.0)
        assertEquals(34300.9, route.durationS, 1.0)
        assertEquals(ManeuverKind.Arrive, route.maneuvers.last().kind)
        // 案内の位置は、いずれも道のりの中に収まり、順番どおり
        val ats = route.maneuvers.map { it.atM }
        assertEquals(ats.sorted(), ats)
        assertTrue(ats.all { it in 0.0..route.distanceM + 1.0 })
    }

    @Test
    fun `高速道路を使ってしまった応答は、候補にしない`() {
        assertNull(ValhallaParser.parseTollFree(response(hasHighway = true)))
    }

    @Test
    fun `応答に経路が無いときは、候補にしない`() {
        assertNull(ValhallaParser.parseTollFree("""{"error_code":171,"error":"No path could be found"}"""))
    }

    @Test
    fun `polylineの符号を、緯度経度に戻せる`() {
        // Google の公式の例(精度5)。38.5,-120.2 / 40.7,-120.95 / 43.252,-126.453
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(3, points.size)
        assertLatLon(LatLon(38.5, -120.2), points[0])
        assertLatLon(LatLon(40.7, -120.95), points[1])
        assertLatLon(LatLon(43.252, -126.453), points[2])
    }

    private fun assertLatLon(expected: LatLon, actual: LatLon) {
        assertEquals(expected.lat, actual.lat, 1e-5)
        assertEquals(expected.lon, actual.lon, 1e-5)
    }
}
