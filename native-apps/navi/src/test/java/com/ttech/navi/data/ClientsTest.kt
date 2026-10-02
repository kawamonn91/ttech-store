package com.ttech.navi.data

import com.ttech.navi.domain.NaviException
import com.ttech.navi.domain.Place
import com.ttech.track.domain.LatLon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ParsersTest {
    @Test
    fun `地名検索の応答、駅の名前に「駅」を付け、住所は小さい順から大きい順に並べ直す`() {
        val json = """
            [
              {"place_id":1,"lat":"38.2598526","lon":"140.8827912","category":"railway","type":"stop","name":"仙台",
               "display_name":"仙台, 杜の陽だまりガレリア(仙台駅東西自由通路), 中央一丁目, 中央, 青葉区, 仙台市, 宮城県, 980-8487, 日本"},
              {"place_id":2,"lat":"37.7550829","lon":"140.4586874","category":"building","type":"train_station","name":"福島駅",
               "display_name":"福島駅, 東西自由通路, 栄町, 福島市, 福島県, 960-8031, 日本"},
              {"place_id":3,"lat":"x","lon":"y","name":"壊れた行","display_name":"壊れた行"}
            ]
        """.trimIndent()
        val places = NominatimClient.parse(json)
        assertEquals(2, places.size)
        assertEquals("仙台駅", places[0].name)
        assertEquals("宮城県仙台市青葉区", places[0].detail)
        assertEquals(38.2598526, places[0].lat, 1e-9)
        assertEquals("福島駅", places[1].name)
        assertEquals("福島県福島市栄町", places[1].detail)
    }

    @Test
    fun `地名検索の応答が壊れていても、空として扱う`() {
        assertTrue(NominatimClient.parse("これはJSONではない").isEmpty())
        assertTrue(NominatimClient.parse("{}").isEmpty())
    }

    @Test
    fun `住所検索の応答、入力した文字を含むものだけを残す`() {
        val json = """
            [
              {"geometry":{"coordinates":[139.930379,37.508795],"type":"Point"},"type":"Feature","properties":{"addressCode":"7202","title":"会津若松駅","dataSource":"1"}},
              {"geometry":{"coordinates":[139.929626,37.494431],"type":"Point"},"type":"Feature","properties":{"addressCode":"","title":"福島県会津若松市"}}
            ]
        """.trimIndent()
        val places = GsiClient.parseSearch(json, "会津若松駅")
        assertEquals(listOf("会津若松駅"), places.map { it.name })
        assertEquals(37.508795, places[0].lat, 1e-9)
        assertEquals(139.930379, places[0].lon, 1e-9)
    }

    @Test
    fun `住所のように数字を含む入力は、全角・漢数字の表記の違いがあっても、結果を残す`() {
        val json = """[{"geometry":{"coordinates":[140.881699,38.260338],"type":"Point"},"type":"Feature","properties":{"title":"宮城県仙台市青葉区中央一丁目１番"}}]"""
        assertEquals(1, GsiClient.parseSearch(json, "仙台市青葉区中央1-1-1").size)
        // 数字を含まない入力(地名)では、まったく関係のない部分一致は捨てる
        assertTrue(GsiClient.parseSearch(json, "土湯峠").isEmpty())
    }

    @Test
    fun `逆ジオコーダーの応答、市区町村コードと、町の名前(大字は取る)`() {
        val r = GsiClient.parseReverse("""{"results":{"muniCd":"07408","lv01Nm":"大字若宮"}}""")
        assertNotNull(r)
        assertEquals(7408, r!!.muniCd)
        assertEquals("若宮", r.town)
        assertNull(GsiClient.parseReverse("""{"results":null}"""))
        assertNull(GsiClient.parseReverse("壊れている"))
    }

    @Test
    fun `表記の違いをならして比べる`() {
        assertEquals(normalize("仙台市青葉区中央１－１－１"), normalize("仙台市 青葉区 中央1-1-1"))
        assertEquals(normalize("ＡＢＣ"), normalize("abc"))
    }
}

class PlaceSearchMergeTest {
    private fun p(name: String, lat: Double, lon: Double) = Place(name, "", lat, lon)

    @Test
    fun `近い場所の重複を除き、名前が一致する駅を先にする`() {
        val near = LatLon(37.5, 139.9) // 会津若松
        val list = listOf(
            p("仙台駅前開発ビル", 38.2601, 140.8804),
            p("仙台駅", 38.2598, 140.8828),
            p("仙台駅", 38.2600, 140.8830), // 100mほどの重複
            p("仙台駅", 34.0, 133.0), // 遠く離れた同名(別の場所)
        )
        val merged = PlaceSearch.merge("仙台駅", list, near)
        assertEquals(3, merged.size) // 重複1件を除く
        assertEquals("仙台駅", merged[0].name)
        assertEquals(38.2598, merged[0].lat, 1e-9) // 近いほうが先
        assertEquals("仙台駅前開発ビル", merged.first { it.name != "仙台駅" }.name)
    }

    @Test
    fun `現在地が分からなくても並べられ、件数は上限まで`() {
        val list = (1..20).map { p("場所$it", 35.0 + it * 0.01, 139.0) }
        assertEquals(10, PlaceSearch.merge("場所", list, null).size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {
    @Test
    fun `呼び出しの間隔を、決めた時間だけあける`() = runTest {
        val limiter = RateLimiter(1000, now = { currentTime })
        limiter.run { }
        assertEquals(0, currentTime) // 最初は待たない
        limiter.run { }
        assertEquals(1000, currentTime)
        limiter.run { }
        assertEquals(2000, currentTime)
    }
}

class HttpClientsTest {
    private lateinit var server: MockWebServer
    private lateinit var http: NaviHttp

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        http = NaviHttp(OkHttpClient(), "T-tech-Navi/test (Android; contact: test@example.com)")
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test
    fun `経路検索は、出発地と目的地を経度,緯度の順に付けて頼み、アプリを名乗る`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(javaClass.classLoader!!.getResourceAsStream("osrm_short.json")!!.bufferedReader().readText()).build())
        val route = OsrmClient(http, base()).route(LatLon(37.5021, 139.9310), LatLon(37.4689, 139.9660))
        assertEquals(6069.0, route.distanceM, 60.0)
        val req = server.takeRequest()
        assertEquals("/route/v1/driving/139.931000,37.502100;139.966000,37.468900", req.url.encodedPath)
        assertEquals("full", req.url.queryParameter("overview"))
        assertEquals("true", req.url.queryParameter("steps"))
        assertEquals("geojson", req.url.queryParameter("geometries"))
        assertEquals("T-tech-Navi/test (Android; contact: test@example.com)", req.headers["User-Agent"])
    }

    @Test
    fun `ルートが無い応答(400と本文)は、分かりやすい文言にする`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(400).body("""{"code":"NoRoute","message":"Impossible route between points"}""").build())
        try {
            OsrmClient(http, base()).route(LatLon(37.0, 139.0), LatLon(35.0, 139.0))
            fail()
        } catch (e: NaviException) {
            assertTrue(e.message!!, e.message!!.contains("ルートが見つかりませんでした"))
        }
    }

    @Test
    fun `サービスが混み合っている(429)ときは、その旨を伝える`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).build())
        try {
            http.get(base() + "/x")
            fail()
        } catch (e: NaviException) {
            assertTrue(e.message!!.contains("混み合って"))
        }
    }

    @Test
    fun `天気予報は、複数の地点を1回でまとめて頼む`() = runBlocking {
        val body = """
            [{"hourly":{"time":["2026-09-28T09:00"],"weather_code":[61],"precipitation_probability":[80],"precipitation":[1.2],"temperature_2m":[19.0]}},
             {"hourly":{"time":["2026-09-28T09:00"],"weather_code":[0],"precipitation_probability":[0],"precipitation":[0.0],"temperature_2m":[21.0]}}]
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(body).build())
        val list = OpenMeteoClient(http, base()).forecast(listOf(LatLon(38.2601, 140.8824), LatLon(37.5021, 139.9310)))
        assertEquals(2, list.size)
        val req = server.takeRequest()
        assertEquals("38.2601,37.5021", req.url.queryParameter("latitude"))
        assertEquals("140.8824,139.9310", req.url.queryParameter("longitude"))
        assertEquals("Asia/Tokyo", req.url.queryParameter("timezone"))
    }

    @Test
    fun `地点の数と、予報の数が合わないときは、エラーにする`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"hourly":{"time":["2026-09-28T09:00"],"weather_code":[0]}}""").build())
        try {
            OpenMeteoClient(http, base()).forecast(listOf(LatLon(38.0, 140.0), LatLon(37.0, 139.0)))
            fail()
        } catch (e: NaviException) {
            assertTrue(e.message!!.contains("合いません"))
        }
    }

    @Test
    fun `逆ジオコーダーは、緯度と経度を付けて頼む`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"results":{"muniCd":"07202","lv01Nm":"大字中央"}}""").build())
        val r = GsiClient(http, reverseBase = base(), searchBase = base()).reverse(37.5021, 139.9310)
        assertEquals(7202, r!!.muniCd)
        val req = server.takeRequest()
        assertEquals("/reverse-geocoder/LonLatToAddress", req.url.encodedPath)
        assertEquals("37.502100", req.url.queryParameter("lat"))
        assertEquals("139.931000", req.url.queryParameter("lon"))
    }

    @Test
    fun `地名検索は、日本国内に絞り、まず現在地の近くだけに絞り込んで探す`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").build())
        server.enqueue(MockResponse.Builder().body("[]").build())
        NominatimClient(http, base(), RateLimiter(0)).search("仙台駅", LatLon(37.5, 139.9))
        val req = server.takeRequest()
        assertEquals("jp", req.url.queryParameter("countrycodes"))
        assertEquals("仙台駅", req.url.queryParameter("q"))
        assertEquals("1", req.url.queryParameter("bounded")) // 最初は近くだけに絞り込む
        assertNotNull(req.url.queryParameter("viewbox"))
    }

    @Test
    fun `近くで見つからなければ、絞り込み無しで(全国から)探し直す`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").build()) // 近く: 見つからない
        server.enqueue(MockResponse.Builder().body(sendaiStationJson).build()) // 広く: 見つかる
        val places = NominatimClient(http, base(), RateLimiter(0)).search("仙台駅", LatLon(37.5, 139.9))
        assertEquals(1, places.size)

        val first = server.takeRequest()
        assertEquals("1", first.url.queryParameter("bounded"))
        val second = server.takeRequest()
        assertEquals("0", second.url.queryParameter("bounded")) // 2回目は絞り込み無し

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `現在地が無ければ、絞り込み(viewbox)無しで1回だけ探す`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").build())
        NominatimClient(http, base(), RateLimiter(0)).search("仙台駅", near = null)
        val req = server.takeRequest()
        assertNull(req.url.queryParameter("viewbox"))
        assertEquals(1, server.requestCount)
    }

    private val sendaiStationJson = """
        [{"lat":"38.2602","lon":"140.8827","display_name":"仙台駅, 青葉区, 仙台市, 宮城県, 日本","category":"railway","type":"station","name":"仙台"}]
    """.trimIndent()
}
