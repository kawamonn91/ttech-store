package com.kawamonn.store.auth

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupabaseAuthApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SupabaseAuthApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = SupabaseAuthApi(OkHttpClient(), server.url("/").toString(), "anon-key")
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val sessionJson = """
        {"access_token":"at1","refresh_token":"rt1","expires_in":3600,
         "user":{"id":"u1","email":"a@example.com","email_confirmed_at":"2026-01-01T00:00:00Z"}}
    """.trimIndent()

    @Test
    fun `Googleサインインは正しいパスとボディで呼ぶ`() = runBlocking {
        val session = run {
            server.enqueue(json(sessionJson))
            api.signInWithGoogleIdToken("id-token-abc", "nonce-xyz")
        }

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/auth/v1/token", req.url.encodedPath)
        assertEquals("id_token", req.url.queryParameter("grant_type"))
        assertEquals("anon-key", req.headers["apikey"])
        assertEquals("Bearer anon-key", req.headers["Authorization"]) // 未ログインなのでanonキーで呼ぶ
        val body = req.body!!.utf8()
        assertTrue(body.contains("\"provider\":\"google\""))
        assertTrue(body.contains("\"id_token\":\"id-token-abc\""))
        assertTrue(body.contains("\"nonce\":\"nonce-xyz\""))

        assertEquals("at1", session.accessToken)
        assertEquals("u1", session.user.id)
        assertEquals("a@example.com", session.user.email)
    }

    @Test
    fun `パスワードサインインは正しいgrant_typeで呼ぶ`() = runBlocking {
        server.enqueue(json(sessionJson))
        api.signInWithPassword("a@example.com", "hunter2")
        val req = server.takeRequest()
        assertEquals("password", req.url.queryParameter("grant_type"))
        assertTrue(req.body!!.utf8().contains("hunter2"))
    }

    @Test
    fun `リフレッシュは refresh_token を渡す`() = runBlocking {
        server.enqueue(json(sessionJson))
        api.refresh("old-refresh")
        val req = server.takeRequest()
        assertEquals("refresh_token", req.url.queryParameter("grant_type"))
        assertTrue(req.body!!.utf8().contains("old-refresh"))
    }

    @Test
    fun `signOutはアクセストークンをAuthorizationに使い、失敗しても例外を投げない`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("boom").build())
        api.signOut("my-access-token") // throw しないことを確認するだけ
        val req = server.takeRequest()
        assertEquals("Bearer my-access-token", req.headers["Authorization"])
    }

    @Test
    fun `サーバーのエラーメッセージ(msg)を利用者向けメッセージにする`() {
        server.enqueue(json("""{"msg":"Invalid login credentials"}""", code = 400))
        val e = assertThrows(AuthApiException::class.java) { runBlocking { api.signInWithPassword("a@example.com", "wrong") } }
        assertEquals("Invalid login credentials", e.message)
    }

    @Test
    fun `error_description 形式のエラーにも対応する`() {
        server.enqueue(json("""{"error":"invalid_request","error_description":"id_token required"}""", code = 400))
        val e = assertThrows(AuthApiException::class.java) { runBlocking { api.signInWithGoogleIdToken("", "") } }
        assertEquals("id_token required", e.message)
    }

    @Test
    fun `listFactorsはGET auth-v1-userのfactorsを返す`() = runBlocking {
        server.enqueue(json("""{"id":"u1","factors":[{"id":"f1","factor_type":"totp","status":"verified"}]}"""))
        val factors = api.listFactors("at1")

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/auth/v1/user", req.url.encodedPath)
        assertEquals("Bearer at1", req.headers["Authorization"])
        assertEquals(1, factors.size)
        assertEquals("f1", factors[0].id)
        assertEquals("totp", factors[0].factorType)
        assertEquals("verified", factors[0].status)
    }

    @Test
    fun `challengeTotpは正しいパスでPOSTしchallenge_idを返す`() = runBlocking {
        server.enqueue(json("""{"id":"challenge-abc","expires_at":123}"""))
        val challengeId = api.challengeTotp("at1", "factor-1")

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/auth/v1/factors/factor-1/challenge", req.url.encodedPath)
        assertEquals("Bearer at1", req.headers["Authorization"])
        assertEquals("challenge-abc", challengeId)
    }

    @Test
    fun `verifyTotpはchallenge_idとcodeを渡し新しいセッションを返す`() = runBlocking {
        server.enqueue(json(sessionJson))
        val session = api.verifyTotp("at1", "factor-1", "challenge-abc", "123456")

        val req = server.takeRequest()
        assertEquals("/auth/v1/factors/factor-1/verify", req.url.encodedPath)
        val body = req.body!!.utf8()
        assertTrue(body.contains("\"challenge_id\":\"challenge-abc\""))
        assertTrue(body.contains("\"code\":\"123456\""))
        assertEquals("at1", session.accessToken)
    }

    @Test
    fun `verifyTotpのコード不一致エラーは日本語のメッセージにする`() {
        server.enqueue(json("""{"code":400,"error_code":"mfa_verification_failed","msg":"Invalid TOTP code entered"}""", code = 400))
        val e = assertThrows(AuthApiException::class.java) {
            runBlocking { api.verifyTotp("at1", "factor-1", "challenge-abc", "000000") }
        }
        assertTrue(e.message!!.startsWith("確認コードが正しくありません"))
    }

    @Test
    fun `リフレッシュトークンが無効なら、ログインし直す案内にする`() {
        server.enqueue(json("""{"code":400,"error_code":"refresh_token_already_used","msg":"Invalid Refresh Token"}""", code = 400))
        val e = assertThrows(AuthApiException::class.java) { runBlocking { api.refresh("old-refresh") } }
        assertTrue(e.message!!.contains("もう一度ログイン"))
    }

    @Test
    fun `接続できない場合は通信エラーになる`() {
        val port = server.port
        server.close()
        val offline = SupabaseAuthApi(OkHttpClient(), "http://localhost:$port/", "anon-key")
        val e = assertThrows(AuthApiException::class.java) { runBlocking { offline.signInWithPassword("a@example.com", "x") } }
        assertTrue(e.message!!.contains("通信"))
    }
}
