package com.ttech.admin.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTokens(var token: String = "t1", var refreshTo: String? = "t2") : TokenSource {
    var refreshCount = 0
    override suspend fun validToken(): String = token
    override suspend fun forceRefresh(): String? { refreshCount++; return refreshTo?.also { token = it } }
}

class AdminApiTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokens
    private lateinit var api: AdminApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = FakeTokens()
        api = AdminApi(OkHttpClient(), server.url("/").toString(), tokens)
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `概要を読み、Bearerトークンを付ける`() = runBlocking {
        server.enqueue(
            json(
                """{"users":{"total":10,"new7d":2,"banned":1},"diary":{"entries":50,"openReports":3},"reviews":{"openReports":1},
                   "developers":{"pending":1},"releases":{"pendingApproval":0},"apps":{"published":52},"downloads":{"total":300,"last7d":20},"future":"ignored"}""",
            ),
        )
        val o = api.overview()
        assertEquals(10, o.users.total)
        assertEquals(4, o.openReports) // 日記3 + レビュー1
        assertEquals(52, o.apps.published)

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/admin/overview", req.url.encodedPath)
        assertEquals("Bearer t1", req.headers["Authorization"])
    }

    @Test
    fun `ユーザー検索は、空の条件をURLに付けない`() = runBlocking {
        server.enqueue(json("""{"items":[],"total":0}"""))
        api.users("", bannedOnly = false)
        val plain = server.takeRequest().url
        assertNull(plain.queryParameter("q"))
        assertNull(plain.queryParameter("banned"))

        server.enqueue(json("""{"items":[{"id":"u1","email":"a@example.com","displayName":"アリス","bannedAt":"2026-09-24T00:00:00Z","diaryCount":3}],"total":1}"""))
        val page = api.users("ali ce", bannedOnly = true, offset = 30)
        val url = server.takeRequest().url
        assertEquals("ali ce", url.queryParameter("q"))
        assertEquals("1", url.queryParameter("banned"))
        assertEquals("30", url.queryParameter("offset"))
        assertTrue(page.items[0].isBanned)
        assertEquals(3, page.items[0].diaryCount)
    }

    @Test
    fun `BANは理由をJSONで送り、解除は空のPOST`() = runBlocking {
        server.enqueue(json("""{"ok":true}"""))
        api.ban("11111111-1111-4111-8111-111111111111", "迷惑行為")
        val ban = server.takeRequest()
        assertEquals("POST", ban.method)
        assertEquals("/api/admin/users/11111111-1111-4111-8111-111111111111/ban", ban.url.encodedPath)
        assertTrue(ban.body!!.utf8().contains("迷惑行為"))

        server.enqueue(json("""{"ok":true}"""))
        api.unban("u2")
        val unban = server.takeRequest()
        assertEquals("POST", unban.method)
        assertEquals("/api/admin/users/u2/unban", unban.url.encodedPath)
    }

    @Test
    fun `報告の処理は種類・ID・操作を送り、理由が空なら送らない`() = runBlocking {
        server.enqueue(json("""{"ok":true}"""))
        api.resolveReport("diary", "r1", "dismiss", "")
        val a = server.takeRequest()
        assertEquals("/api/admin/reports/diary/r1", a.url.encodedPath)
        assertEquals("""{"action":"dismiss"}""", a.body!!.utf8())

        server.enqueue(json("""{"ok":true}"""))
        api.resolveReport("review", "7", "ban_author", "嫌がらせ")
        assertTrue(server.takeRequest().body!!.utf8().contains(""""reason":"嫌がらせ""""))
    }

    @Test
    fun `日記の投稿の削除はDELETE`() = runBlocking {
        server.enqueue(json("""{"ok":true}"""))
        api.deleteDiaryEntry("e1")
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/admin/diary/entries/e1", req.url.encodedPath)
    }

    @Test
    fun `報告の一覧は、既定で未対応のみ。すべて指定ならstatus=all`() = runBlocking {
        server.enqueue(json("""{"items":[{"kind":"diary","id":"r1","reason":"spam","status":"open","reporter":{"id":"u1","name":"花子"},"target":{"userId":"u2","name":"太郎"},"body":"宣伝","contentId":"e1","contentLabel":"ひとこと日記の投稿"}]}"""))
        val open = api.reports(includeResolved = false)
        assertNull(server.takeRequest().url.queryParameter("status"))
        assertEquals("花子", open[0].reporter.name)
        assertEquals("u2", open[0].target.userId)

        server.enqueue(json("""{"items":[]}"""))
        api.reports(includeResolved = true)
        assertEquals("all", server.takeRequest().url.queryParameter("status"))
    }

    @Test
    fun `401ならトークンを更新して1回だけやり直す`() = runBlocking {
        server.enqueue(json("""{"error":"管理者としてログインしてください"}""", 401))
        server.enqueue(json("""{"users":{"total":1}}"""))
        assertEquals(1, api.overview().users.total)
        assertEquals("Bearer t1", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer t2", server.takeRequest().headers["Authorization"])
        assertEquals(1, tokens.refreshCount)
    }

    @Test
    fun `更新しても401なら、サーバーの文言でエラーにする(無限に繰り返さない)`() {
        server.enqueue(json("""{"error":"管理者としてログインしてください"}""", 401))
        server.enqueue(json("""{"error":"管理者としてログインしてください"}""", 401))
        val e = assertThrows(AdminApiException::class.java) { runBlocking { api.overview() } }
        assertEquals(401, e.status)
        assertEquals("管理者としてログインしてください", e.message)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `トークンを更新できなければ SessionExpiredException`() {
        tokens.refreshTo = null
        server.enqueue(json("""{"error":"x"}""", 401))
        assertThrows(SessionExpiredException::class.java) { runBlocking { api.overview() } }
    }

    @Test
    fun `2段階認証が必要と言われたら TotpRequiredException(更新はしない)`() {
        server.enqueue(json("""{"error":"2段階認証が必要です","code":"totp_required"}""", 401))
        assertThrows(TotpRequiredException::class.java) { runBlocking { api.overview() } }
        assertEquals(0, tokens.refreshCount)
    }

    @Test
    fun `操作の失敗は、サーバーの文言と状態コードをそのまま使う`() {
        server.enqueue(json("""{"error":"管理者はBANできません"}""", 409))
        val e = assertThrows(AdminApiException::class.java) { runBlocking { api.ban("u1", "") } }
        assertEquals(409, e.status)
        assertEquals("管理者はBANできません", e.message)
        assertEquals(1, server.requestCount) // 409 はやり直さない
    }

    @Test
    fun `JSONでないエラー応答でも、状態コード付きの文言になる`() {
        server.enqueue(MockResponse.Builder().code(502).body("<html>Bad Gateway</html>").build())
        val e = assertThrows(AdminApiException::class.java) { runBlocking { api.overview() } }
        assertEquals(502, e.status)
        assertTrue(e.message!!.contains("502"))
    }

    @Test
    fun `ユーザー詳細は未知の項目や欠けた項目があっても読める`() = runBlocking {
        server.enqueue(
            json(
                """{"id":"u1","email":"a@example.com","emailConfirmed":true,"displayName":"アリス","role":"user","provider":"google",
                    "createdAt":"2026-09-01T00:00:00Z","lastSignInAt":null,"bannedAt":null,"developer":null,
                    "counts":{"diaryEntries":4,"reviews":1,"reportsAgainst":2,"reportsFiled":0,"downloads":9},
                    "recentEntries":[{"id":"e1","body":"こんにちは","visibility":"public","hasPhoto":true,"createdAt":"t"}],
                    "recentReviews":[],"reportsAgainst":[{"id":"d1","reason":"spam","status":"open","entryBody":"宣伝","createdAt":"t"}],"brandNew":1}""",
            ),
        )
        val d = api.userDetail("u1")
        assertEquals("アリス", d.displayName)
        assertFalse(d.isBanned)
        assertEquals(9, d.counts.downloads)
        assertTrue(d.recentEntries[0].hasPhoto)
        assertEquals("宣伝", d.reportsAgainst[0].entryBody)
    }

    @Test
    fun `通信できないとき(接続できない)はメッセージ付きの例外`() {
        server.close()
        val e = assertThrows(AdminApiException::class.java) { runBlocking { api.overview() } }
        assertEquals(0, e.status)
        assertTrue(e.message!!.contains("通信"))
    }
}
