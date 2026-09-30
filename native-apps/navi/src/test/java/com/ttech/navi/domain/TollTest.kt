package com.ttech.navi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadClassTest {
    @Test
    fun `路線番号がEで始まれば、有料の高速道路とみなす`() {
        assertTrue(isTollRoad("E1", "東名高速道路"))
        assertTrue(isTollRoad("E1A", "○○支線"))
        assertTrue(isTollRoad("466; E83", "第三京浜道路")) // 複数の路線番号のうち、ひとつでも該当すればよい
    }

    @Test
    fun `路線番号がなくても、名前に「高速」「自動車道」を含めば有料とみなす`() {
        assertTrue(isTollRoad(null, "首都高速神奈川1号横羽線"))
        assertTrue(isTollRoad(null, "東北自動車道"))
    }

    @Test
    fun `一般道路(路線番号が数字だけ、名前に高速・自動車道を含まない)は有料とみなさない`() {
        assertFalse(isTollRoad("246", "玉川通り"))
        assertFalse(isTollRoad(null, "中央通り"))
        assertFalse(isTollRoad(null, ""))
    }
}

class TollEstimateTest {
    @Test
    fun `高速道路を通らなければ0円`() {
        assertEquals(0, TollEstimate.estimate(0.0, VehicleClass.Standard))
        assertEquals(0, TollEstimate.estimate(499.0, VehicleClass.Standard)) // ランプなど、ごく短い区間だけは無料扱い
    }

    @Test
    fun `普通車を基準に、車種の倍率がかかる`() {
        val standard = TollEstimate.estimate(20_000.0, VehicleClass.Standard)
        val kei = TollEstimate.estimate(20_000.0, VehicleClass.Kei)
        val large = TollEstimate.estimate(20_000.0, VehicleClass.Large)
        assertTrue("軽自動車は普通車より安い目安 (軽=$kei, 普通=$standard)", kei < standard)
        assertTrue("大型車は普通車より高い目安 (大型=$large, 普通=$standard)", large > standard)
    }

    @Test
    fun `10円単位に丸める`() {
        val yen = TollEstimate.estimate(15_000.0, VehicleClass.Standard)
        assertEquals(0, yen % 10)
    }
}

class RouteChooserTest {
    private fun route(durationS: Double, arriveModifier: String?): Route {
        val line = Polyline(listOf(com.ttech.track.domain.LatLon(35.0, 139.0), com.ttech.track.domain.LatLon(35.01, 139.01)))
        val arrive = Maneuver(ManeuverKind.Arrive, arriveModifier, line.lengthM, "", line.points.last())
        return Route(line, listOf(arrive), doubleArrayOf(0.0), doubleArrayOf(durationS), line.lengthM, durationS)
    }

    @Test
    fun `候補が1つならそれを選ぶ`() {
        val only = route(600.0, "left")
        assertEquals(only, RouteChooser.pickDefault(listOf(only)))
    }

    @Test
    fun `速さが同じくらいなら、目的地に左折で入れる候補を選ぶ`() {
        val right = route(600.0, "right") // いちばん速いが、右折で進入
        val left = route(650.0, "left") // 8%ほど遅いだけ
        assertEquals(left, RouteChooser.pickDefault(listOf(right, left)))
    }

    @Test
    fun `左折の候補がかなり遅いときは、速いほう(右折)を選ぶ`() {
        val right = route(600.0, "right")
        val left = route(1000.0, "left") // 66%も遅い
        assertEquals(right, RouteChooser.pickDefault(listOf(right, left)))
    }

    @Test
    fun `どちらも右折なら、単純にいちばん速いものを選ぶ`() {
        val slow = route(600.0, "right")
        val fast = route(500.0, "right")
        assertEquals(fast, RouteChooser.pickDefault(listOf(slow, fast)))
    }
}

class OsrmParserAlternativesTest {
    /** 高速道路(E1)を通る経路と、一般道だけの経路の、2つの候補を返す応答 */
    private val twoRouteResponse = """
        {
          "code": "Ok",
          "routes": [
            {
              "distance": 10000, "duration": 600,
              "geometry": {"coordinates": [[139.00,35.00],[139.05,35.02],[139.10,35.05]]},
              "legs": [{"steps": [
                {"distance": 3000, "duration": 120, "name": "", "maneuver": {"type":"depart","location":[139.00,35.00]}},
                {"distance": 5000, "duration": 300, "name": "東名高速道路", "ref": "E1", "maneuver": {"type":"on ramp","modifier":"right","location":[139.03,35.01]}},
                {"distance": 2000, "duration": 180, "name": "一般道", "maneuver": {"type":"off ramp","modifier":"left","location":[139.08,35.04]}},
                {"distance": 0, "duration": 0, "name": "", "maneuver": {"type":"arrive","modifier":"right","location":[139.10,35.05]}}
              ]}]
            },
            {
              "distance": 12000, "duration": 900,
              "geometry": {"coordinates": [[139.00,35.00],[139.06,35.03],[139.10,35.05]]},
              "legs": [{"steps": [
                {"distance": 12000, "duration": 900, "name": "国道246号", "ref": "246", "maneuver": {"type":"depart","location":[139.00,35.00]}},
                {"distance": 0, "duration": 0, "name": "", "maneuver": {"type":"arrive","modifier":"left","location":[139.10,35.05]}}
              ]}]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `候補すべてを取り出せる`() {
        val routes = OsrmParser.parseAll(twoRouteResponse)
        assertEquals(2, routes.size)
    }

    @Test
    fun `高速道路の区間の距離を、経路ごとに合計する`() {
        val (highway, local) = OsrmParser.parseAll(twoRouteResponse)
        // ステップの距離は、折れ線の実際の長さに合わせて按分されるので、テストデータの宣言値ちょうどにはならない
        assertEquals(5000.0, highway.tollDistanceM, 1000.0)
        assertEquals(0.0, local.tollDistanceM, 0.001)
    }

    @Test
    fun `parse は最初の候補を返す(既存の呼び出し元との互換性)`() {
        assertEquals(OsrmParser.parseAll(twoRouteResponse).first().distanceM, OsrmParser.parse(twoRouteResponse).distanceM, 0.001)
    }

    @Test
    fun `実際の応答(会津若松の短いルート)は一般道だけなので、高速道路の距離は0`() {
        assertEquals(0.0, Fixtures.shortRoute().tollDistanceM, 0.001)
    }
}
