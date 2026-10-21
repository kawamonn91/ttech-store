package com.kawamonn.store.data.api

import com.kawamonn.store.auth.TotpRequiredException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AdminApiTest {
    private lateinit var server: MockWebServer
    private var token: String? = "admin-token"
    private lateinit var api: AdminApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = AdminApi(OkHttpClient(), server.url("/").toString()) { token }
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `管理者専用アプリの一覧を、管理者のトークンを付けて取得する`() = runBlocking {
        server.enqueue(
            json(
                """{"items":[
                    {"appId":"a1","slug":"tool","name":"ツール","packageName":"com.example.tool","releaseId":"r1","versionName":"1.2","versionCode":5,"apkSize":100},
                    {"appId":"a2","slug":"x","name":"X","packageName":"com.example.x","releaseId":"r2","versionName":null,"versionCode":7,"apkSize":null}
                ]}""",
            ),
        )
        val apps = api.privateApps()
        val req = server.takeRequest()
        assertEquals("/api/admin/private-apps", req.url.encodedPath)
        assertEquals("Bearer admin-token", req.headers["Authorization"])
        assertEquals(listOf("r1", "r2"), apps.map { it.releaseId })
        assertEquals("1.2", apps[0].versionLabel)
        // versionName が null なら versionCode を出す(文字列の "null" にしない)
        assertEquals("7", apps[1].versionLabel)
    }

    @Test
    fun `リリースの承認は、操作をJSONでPOSTする`() = runBlocking {
        server.enqueue(json("""{"ok":true}"""))
        api.releaseDecision("rel-1", "publish")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/admin/releases/rel-1/decision", req.url.encodedPath)
        assertEquals("""{"action":"publish"}""", req.body!!.utf8())
        assertEquals("Bearer admin-token", req.headers["Authorization"])
    }

    @Test
    fun `2段階認証が必要と言われたら TotpRequiredException`() {
        server.enqueue(json("""{"error":"2段階認証が必要です","code":"totp_required"}""", code = 401))
        assertThrows(TotpRequiredException::class.java) { runBlocking { api.releaseDecision("rel-1", "reject") } }
    }

    @Test
    fun `エラーはサーバーの文言、無ければ状態コード付きの汎用メッセージにする`() {
        server.enqueue(json("""{"error":"検査が完了したリリースだけ公開できます"}""", code = 409))
        val e = assertThrows(ApiException::class.java) { runBlocking { api.releaseDecision("rel-1", "publish") } }
        assertEquals("検査が完了したリリースだけ公開できます", e.message)
        assertEquals(409, e.status)

        server.enqueue(MockResponse.Builder().code(502).body("Bad Gateway").build())
        val generic = assertThrows(ApiException::class.java) { runBlocking { api.privateApps() } }
        assertEquals("管理者用アプリを読み込めませんでした (502)", generic.message)
    }

    @Test
    fun `ログインしていなければ、リクエストを送らない`() {
        token = null
        assertThrows(ApiException::class.java) { runBlocking { api.privateApps() } }
        assertEquals(0, server.requestCount)
    }
}
