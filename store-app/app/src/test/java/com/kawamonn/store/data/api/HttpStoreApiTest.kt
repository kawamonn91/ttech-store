package com.kawamonn.store.data.api

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpStoreApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HttpStoreApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpStoreApi(OkHttpClient(), server.url("/api/v1").toString())
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val summaryJson = """
        {"id":"a1","slug":"yomumemo","packageName":"jp.yomumemo.app","name":"ヨムメモ","shortDesc":"読書メモ",
         "iconUrl":null,"category":{"slug":"hobby","name":"趣味"},"downloadCount":12,"ratingAvg":4.5,"ratingCount":2,
         "latest":{"releaseId":"r1","versionName":"1.0.0","versionCode":3,"apkSize":1000,"minSdk":26,
                   "permissions":["android.permission.INTERNET"],"releaseNotes":"初回","publishedAt":null}}
    """.trimIndent()

    @Test
    fun `一覧を取得でき、未知のフィールドは無視される`() = runBlocking {
        server.enqueue(json("""{"items":[${summaryJson.replace("\"id\"", "\"future\":1,\"id\"")}],"total":1}"""))
        val result = api.apps(query = null, category = null, sort = "popular", limit = 10, offset = 0)
        assertEquals(1, result.total)
        val app = result.items.single()
        assertEquals("jp.yomumemo.app", app.packageName)
        assertEquals(3L, app.latest?.versionCode)
        assertNull(app.iconUrl)
    }

    @Test
    fun `検索条件はクエリパラメータとして正しくエンコードされ、空のqは送らない`() = runBlocking {
        server.enqueue(json("""{"items":[],"total":0}"""))
        server.enqueue(json("""{"items":[],"total":0}"""))

        api.apps(query = "読書 メモ&x", category = "hobby", sort = "new", limit = 50, offset = 10)
        val first = server.takeRequest().url
        assertEquals("読書 メモ&x", first.queryParameter("q"))
        assertEquals("hobby", first.queryParameter("category"))
        assertEquals("new", first.queryParameter("sort"))
        assertEquals("50", first.queryParameter("limit"))
        assertEquals("10", first.queryParameter("offset"))

        api.apps(query = "  ", category = null, sort = "new", limit = 1, offset = 0)
        val second = server.takeRequest().url
        assertNull(second.queryParameter("q"))
        assertNull(second.queryParameter("category"))
    }

    @Test
    fun `詳細はスラッグのパスで取得する`() = runBlocking {
        server.enqueue(json("""{"id":"a1","slug":"yomumemo","packageName":"p.q","name":"N","screenshots":["u1"],"developerName":"T-tech"}"""))
        val detail = api.app("yomumemo")
        assertEquals("/api/v1/apps/yomumemo", server.takeRequest().url.encodedPath)
        assertEquals(listOf("u1"), detail.screenshots)
        assertEquals("T-tech", detail.developerName)
        assertNull(detail.latest)
    }

    @Test
    fun `ダウンロード情報の取得は端末IDをJSONでPOSTする`() = runBlocking {
        server.enqueue(
            json(
                """{"url":"https://r2.example/x.apk?sig=1","sha256":"${"a".repeat(64)}","signingCertSha256":"${"b".repeat(64)}",
                    "apkSize":10,"versionCode":3,"packageName":"jp.yomumemo.app"}""",
            ),
        )
        val info = api.downloadInfo("rel-1", "device-abc")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/releases/rel-1/download", req.url.encodedPath)
        assertTrue(req.body!!.utf8().contains("\"deviceId\":\"device-abc\""))
        assertEquals("a".repeat(64), info.sha256)
        assertEquals(3L, info.versionCode)
    }

    @Test
    fun `エラーレスポンスの error を利用者向けメッセージとして返す`() {
        server.enqueue(json("""{"error":"このアプリは現在ダウンロードできません"}""", code = 404))
        val e = assertThrows(ApiException::class.java) { runBlocking { api.downloadInfo("x", "device-abc") } }
        assertEquals("このアプリは現在ダウンロードできません", e.message)
        assertEquals(404, e.status)
    }

    @Test
    fun `JSONでないエラー本文でも状態コード付きの汎用メッセージにする`() {
        server.enqueue(MockResponse.Builder().code(502).body("<html>Bad Gateway</html>").build())
        val e = assertThrows(ApiException::class.java) { runBlocking { api.home() } }
        assertEquals(502, e.status)
        assertTrue(e.message!!.contains("502"))
    }

    @Test
    fun `壊れたJSONは解釈エラーになる`() {
        server.enqueue(json("{not json"))
        val e = assertThrows(ApiException::class.java) { runBlocking { api.home() } }
        assertNotNull(e.message)
        assertTrue(e.message!!.contains("解釈"))
    }

    @Test
    fun `接続できない場合は通信エラーとして扱う`() {
        val closedPort = server.port
        server.close()
        val offline = HttpStoreApi(OkHttpClient(), "http://localhost:$closedPort/api/v1")
        val e = assertThrows(ApiException::class.java) { runBlocking { offline.home() } }
        assertNull(e.status)
        assertTrue(e.message!!.contains("通信"))
    }
}
