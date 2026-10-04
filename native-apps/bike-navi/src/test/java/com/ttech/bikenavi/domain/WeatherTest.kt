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
}
