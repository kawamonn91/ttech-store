package com.ttech.bikenavi.domain

import com.ttech.track.domain.LatLon
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private fun jst(hour: Int, min: Int): Long =
        ZonedDateTime.of(2026, 5, 1, hour, min, 0, 0, Phrases.JST).toInstant().toEpochMilli()

    /** 直線で、距離[distM]・所要時間[durationS]のルート(自転車なので、車より遅いペースを想定) */
    private fun straightRoute(distM: Double, durationS: Double): Route {
        val a = LatLon(35.0, 139.0)
        val b = LatLon(35.0, 139.0 + distM / 91_000.0)
        val line = Polyline(listOf(a, b))
        return Route(line, listOf(Maneuver(ManeuverKind.Arrive, null, distM, b)), distM, durationS, 0.0, 0.0, emptyList())
    }

    @Test
    fun `速さ(ペース)に応じて、天気を調べる間隔が決まる`() {
        // 時速18kmで4時間のルート(72km) → 1時間ごとに区切ると、3〜4地点くらいになるはず
        val route = straightRoute(distM = 72_000.0, durationS = 4 * 3600.0)
        val samples = WeatherPlanner.samplePoints(route, departMs = jst(8, 0), intervalHours = 1.0)
        assertTrue("地点数: ${samples.size}", samples.size in 3..5)
        assertTrue(samples.last().isDestination)
        assertEquals(route.distanceM, samples.last().progressM, 0.01)
    }

    @Test
    fun `短いルートでは、目的地の1地点だけになる`() {
        val route = straightRoute(distM = 5_000.0, durationS = 1000.0)
        val samples = WeatherPlanner.samplePoints(route, departMs = jst(8, 0), intervalHours = 1.0)
        assertEquals(1, samples.size)
        assertTrue(samples[0].isDestination)
    }

    @Test
    fun `案内文は、晴れのときシンプルになる`() {
        val route = straightRoute(distM = 5_000.0, durationS = 1000.0)
        val samples = WeatherPlanner.samplePoints(route, departMs = jst(8, 0), intervalHours = 1.0)
        val sunny = Weather(samples[0].etaMs, code = 0, popPercent = 0, precipMm = 0.0, tempC = 20.0)
        val lines = WeatherPlanner.briefing(samples, listOf(sunny), listOf(null))
        assertTrue(lines[0].contains("快晴"))
        assertTrue(lines.any { it.contains("雨や雪の心配はなさそう") })
    }

    private fun rain(etaMs: Long) = Weather(etaMs, code = 61, popPercent = 80, precipMm = 1.0, tempC = 18.0)
    private fun sunny(etaMs: Long) = Weather(etaMs, code = 0, popPercent = 0, precipMm = 0.0, tempC = 20.0)
    private fun point(progressM: Double, w: Weather, isDestination: Boolean = false) =
        SamplePoint(progressM, LatLon(35.0, 139.0), w.timeMs, isDestination)

    @Test
    fun `雨の地点に近づくと、1回だけ声かけする`() {
        val w = rain(jst(9, 0))
        val samples = listOf(point(10_000.0, w))
        val engine = WeatherAlertEngine(samples, listOf(w), aheadM = 3_000.0)

        assertEquals(null, engine.update(progressM = 5_000.0))
        val first = engine.update(progressM = 7_500.0)
        assertTrue(first != null && first.contains("雨"))
        assertEquals(null, engine.update(progressM = 8_000.0))
        assertEquals(null, engine.update(progressM = 10_500.0))
    }

    @Test
    fun `晴れの地点は声かけしない`() {
        val w = sunny(jst(9, 0))
        val samples = listOf(point(10_000.0, w))
        val engine = WeatherAlertEngine(samples, listOf(w), aheadM = 3_000.0)
        assertEquals(null, engine.update(progressM = 9_000.0))
        assertEquals(null, engine.update(progressM = 10_000.0))
    }

    @Test
    fun `手前の地点から順に、1件ずつ声かけする`() {
        val w1 = rain(jst(9, 0))
        val w2 = rain(jst(10, 0))
        val samples = listOf(point(5_000.0, w1), point(15_000.0, w2))
        val engine = WeatherAlertEngine(samples, listOf(w1, w2), aheadM = 3_000.0)

        val first = engine.update(progressM = 3_000.0)
        assertTrue("1件目のはず: $first", first != null)
        assertEquals(null, engine.update(progressM = 4_000.0))
        val second = engine.update(progressM = 13_000.0)
        assertTrue("2件目のはず: $second", second != null)
    }

    @Test
    fun `気づかないうちに通り過ぎた地点は、あとから知らせない`() {
        val w = rain(jst(9, 0))
        val samples = listOf(point(5_000.0, w))
        val engine = WeatherAlertEngine(samples, listOf(w), aheadM = 3_000.0)
        // 最初の更新がすでに地点の先(アプリを閉じていた・GPSが飛んだ、など)
        assertEquals(null, engine.update(progressM = 8_000.0))
        assertEquals(null, engine.update(progressM = 9_000.0))
    }
}
