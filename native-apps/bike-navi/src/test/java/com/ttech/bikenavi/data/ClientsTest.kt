package com.ttech.bikenavi.data

import com.ttech.bikenavi.domain.RouteStyle
import com.ttech.track.domain.LatLon
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OverpassParseTest {
    @Test
    fun `Overpassの応答から、種類ごとに場所を取り出し、近い順に並べる`() {
        val center = LatLon(35.0, 139.0)
        val json = """
            {"elements":[
              {"type":"node","lat":35.002,"lon":139.000,"tags":{"shop":"convenience","name":"セブンイレブン渋谷○○店"}},
              {"type":"node","lat":35.001,"lon":139.000,"tags":{"amenity":"restaurant","name":"定食屋"}},
              {"type":"node","lat":35.0005,"lon":139.000,"tags":{"highway":"services","name":"道の駅○○"}},
              {"type":"node","lat":35.01,"lon":139.000,"tags":{"shop":"bakery","name":"パン屋(対象外)"}}
            ]}
        """.trimIndent()
        val stops = OverpassClient.parse(json, center)
        assertEquals(3, stops.size) // パン屋は対象外
        assertEquals(StopKind.RoadsideStation, stops[0].kind) // いちばん近い
        assertTrue(stops[0].distanceFromRouteM < stops[1].distanceFromRouteM)
        assertTrue(stops.any { it.kind == StopKind.Convenience })
        assertTrue(stops.any { it.kind == StopKind.Restaurant })
    }

    @Test
    fun `壊れた応答は空のリストにする`() {
        assertEquals(0, OverpassClient.parse("これはJSONではない", LatLon(35.0, 139.0)).size)
    }
}

class NominatimClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: BikeHttp

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        http = BikeHttp(OkHttpClient(), "T-tech-BikeNavi/test (Android; contact: test@example.com)")
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test
    fun `地名検索は、まず現在地の近くだけに絞り込んで探す`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body(
                """[{"lat":"35.0","lon":"139.0","display_name":"セブンイレブン, 日本"}]""",
            ).build(),
        )
        NominatimClient(http, base(), RateLimiter(0)).search("セブンイレブン", LatLon(35.6595, 139.7016))
        val req = server.takeRequest()
        assertEquals("1", req.url.queryParameter("bounded"))
    }

    @Test
    fun `近くで見つからなければ、絞り込み無しで探し直す`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").build())
        server.enqueue(
            MockResponse.Builder().body(
                """[{"lat":"35.0","lon":"139.0","display_name":"セブンイレブン, 日本"}]""",
            ).build(),
        )
        val places = NominatimClient(http, base(), RateLimiter(0)).search("セブンイレブン", LatLon(35.6595, 139.7016))
        assertEquals(1, places.size)
        server.takeRequest() // 1回目(近く、見つからない)
        val second = server.takeRequest() // 2回目(広く)
        assertEquals("0", second.url.queryParameter("bounded"))
    }

    @Test
    fun `現在地が無ければ、viewbox無しで1回だけ探す`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").build())
        NominatimClient(http, base(), RateLimiter(0)).search("セブンイレブン", near = null)
        assertNull(server.takeRequest().url.queryParameter("viewbox"))
        assertEquals(1, server.requestCount)
    }
}

class BRouterClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: BikeHttp

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        http = BikeHttp(OkHttpClient(), "T-tech-BikeNavi/test (Android; contact: test@example.com)")
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test
    fun `経路検索は、出発地と目的地を経度と緯度で頼み、選んだ走り方を指定する`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body(javaClass.classLoader!!.getResourceAsStream("brouter_short.json")!!.bufferedReader().readText()).build(),
        )
        val route = BRouterClient(http, base()).route(LatLon(35.6595, 139.7016), LatLon(35.6450, 139.7250), RouteStyle.Safety)
        assertTrue(route.distanceM > 0)
        val req = server.takeRequest()
        assertEquals("safety", req.url.queryParameter("profile"))
        assertTrue(req.url.queryParameter("lonlats")!!.startsWith("139.701600,35.659500"))
    }
}
