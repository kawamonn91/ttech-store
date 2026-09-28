package com.ttech.navi.domain

import com.ttech.track.domain.LatLon
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private fun jst(h: Int, m: Int): Long = LocalDateTime.of(2026, 9, 28, h, m).atZone(Phrases.JST).toInstant().toEpochMilli()

    /** 北へ100km・100分(6000秒)の、まっすぐなルート */
    private fun straightRoute(): Route {
        val a = LatLon(37.0, 139.0)
        val b = LatLon(37.0 + 100_000.0 / Polyline.METERS_PER_DEG, 139.0)
        val line = Polyline(listOf(a, b))
        return Route(line, listOf(Maneuver(ManeuverKind.Arrive, null, line.lengthM, "", b)), doubleArrayOf(0.0), doubleArrayOf(6000.0), line.lengthM, 6000.0)
    }

    private fun w(t: Long, code: Int, pop: Int = 0, mm: Double = 0.0, temp: Double? = 20.0) = Weather(t, code, pop, mm, temp)

    @Test
    fun `天気コードを言葉にし、雨・雪などの度合いに分ける`() {
        assertEquals("快晴", WeatherCodes.label(0))
        assertEquals("雨", WeatherCodes.label(63))
        assertEquals("雷雨", WeatherCodes.label(95))
        assertEquals(Precip.None, WeatherCodes.classify(w(0, 1, pop = 20)))
        assertEquals(Precip.RainPossible, WeatherCodes.classify(w(0, 3, pop = 70)))
        assertEquals(Precip.Rain, WeatherCodes.classify(w(0, 61)))
        assertEquals(Precip.Rain, WeatherCodes.classify(w(0, 3, mm = 1.0)))
        assertEquals(Precip.HeavyRain, WeatherCodes.classify(w(0, 65)))
        assertEquals(Precip.Snow, WeatherCodes.classify(w(0, 71)))
        assertEquals(Precip.Thunder, WeatherCodes.classify(w(0, 96)))
    }

    @Test
    fun `天気を調べる地点は、20kmごとで、最後は目的地。通る時刻はルートの時間から求める`() {
        val samples = WeatherPlanner.samplePoints(straightRoute(), departMs = jst(8, 0))
        assertEquals(5, samples.size)
        assertEquals(20_000.0, samples[0].progressM, 5.0)
        assertEquals(jst(8, 20), samples[0].etaMs)
        assertEquals(jst(9, 40), samples.last().etaMs)
        assertTrue(samples.last().isDestination)
        assertEquals(1, samples.count { it.isDestination })
    }

    @Test
    fun `短いルートは、目的地だけを調べる`() {
        val a = LatLon(37.0, 139.0)
        val b = LatLon(37.0 + 5_000.0 / Polyline.METERS_PER_DEG, 139.0)
        val line = Polyline(listOf(a, b))
        val route = Route(line, listOf(Maneuver(ManeuverKind.Arrive, null, line.lengthM, "", b)), doubleArrayOf(0.0), doubleArrayOf(400.0), line.lengthM, 400.0)
        assertEquals(1, WeatherPlanner.samplePoints(route, jst(8, 0)).size)
    }

    @Test
    fun `道中に雨が続く場所があれば、時刻と場所を知らせる`() {
        val samples = WeatherPlanner.samplePoints(straightRoute(), departMs = jst(8, 0))
        val weathers = listOf(w(0, 1), w(0, 61), w(0, 63), w(0, 3), w(0, 1, temp = 21.4))
        val places = listOf("会津若松市", "猪苗代町", "福島市土湯温泉町", "二本松市", "仙台市青葉区")
        val lines = WeatherPlanner.briefing(samples, weathers, places)
        assertEquals("目的地の9時40分ごろの天気は、晴れ、気温は21度の予報です。", lines[0])
        assertEquals("8時40分ごろから9時ごろにかけて、猪苗代町から福島市土湯温泉町付近にかけて雨の可能性があります。", lines[1])
        assertEquals(2, lines.size)
    }

    @Test
    fun `雨が1か所だけなら、その場所を言う。雨が無ければ、心配なしと言う`() {
        val samples = WeatherPlanner.samplePoints(straightRoute(), departMs = jst(8, 0))
        val single = WeatherPlanner.briefing(
            samples,
            listOf(w(0, 1), w(0, 1), w(0, 95), w(0, 1), w(0, 1)),
            listOf("A市", "B町", "土湯温泉町", "D市", "E市"),
        )
        assertEquals("9時ごろ、土湯温泉町付近で雷雨の可能性があります。", single[1])

        val none = WeatherPlanner.briefing(samples, List(5) { w(0, 1) }, List(5) { "X" })
        assertEquals("道中は、雨や雪の心配はなさそうです。", none[1])
    }

    @Test
    fun `予報が取れなかったときは、その旨を言う`() {
        val samples = WeatherPlanner.samplePoints(straightRoute(), departMs = jst(8, 0))
        val lines = WeatherPlanner.briefing(samples, List(5) { null }, List(5) { null })
        assertEquals(listOf("目的地の天気予報は、取得できませんでした。"), lines)
    }

    @Test
    fun `Open-Meteoの応答(複数地点は配列)を読み取れる`() {
        val json = """
            [
              {"latitude":38.25,"longitude":140.875,"hourly":{
                "time":["2026-09-28T08:00","2026-09-28T09:00","2026-09-28T10:00"],
                "weather_code":[1,61,3],"precipitation_probability":[10,80,null],
                "precipitation":[0.0,1.2,0.0],"temperature_2m":[18.5,19.0,null]}},
              {"latitude":37.5,"longitude":139.9,"hourly":{
                "time":["2026-09-28T08:00"],"weather_code":[0],"precipitation_probability":[0],
                "precipitation":[0.0],"temperature_2m":[16.0]}}
            ]
        """.trimIndent()
        val list = OpenMeteoParser.parse(json)
        assertEquals(2, list.size)
        val at9 = list[0].at(jst(9, 5))
        assertNotNull(at9)
        assertEquals(61, at9!!.code)
        assertEquals(80, at9.popPercent)
        assertEquals(19.0, at9.tempC!!, 0.001)
        assertNull(list[0].at(jst(10, 0))!!.tempC) // 値が無い時間は、気温なし
        assertNull(list[0].at(jst(18, 0))) // 予報の範囲の外
        // 地点が1つだけのときは、配列ではなくオブジェクトで返ってくる
        val single = OpenMeteoParser.parse("""{"hourly":{"time":["2026-09-28T08:00"],"weather_code":[2],"precipitation_probability":[5],"precipitation":[0.0],"temperature_2m":[15.0]}}""")
        assertEquals(1, single.size)
        assertEquals(2, single[0].at(jst(8, 10))!!.code)
    }
}
