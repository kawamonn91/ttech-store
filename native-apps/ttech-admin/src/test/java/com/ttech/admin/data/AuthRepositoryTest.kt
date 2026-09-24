package com.ttech.admin.data

import com.ttech.admin.domain.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryStore(var saved: Session? = null) : SessionStore {
    var saveCount = 0
    override fun load(): Session? = saved
    override fun save(session: Session?) { saved = session; saveCount++ }
}

private class FakeAuthApi : AuthApi {
    var loginError: AuthApiException? = null
    var factors: List<TotpFactor> = emptyList()
    var refreshError: AuthApiException? = null
    var verifyError: AuthApiException? = null
    val calls = mutableListOf<String>()
    private var counter = 0

    private fun tokens(prefix: String) = AuthTokens("$prefix-access-${++counter}", "$prefix-refresh-$counter", 3600, null, AuthUser("admin-1", "admin@example.com"))

    override suspend fun signInWithPassword(email: String, password: String): AuthTokens {
        calls += "login:$email"
        loginError?.let { throw it }
        return tokens("pw")
    }

    override suspend fun refresh(refreshToken: String): AuthTokens {
        calls += "refresh:$refreshToken"
        refreshError?.let { throw it }
        return tokens("re")
    }

    override suspend fun listTotpFactors(accessToken: String): List<TotpFactor> = factors

    override suspend fun challengeTotp(accessToken: String, factorId: String): String = "challenge-1"

    override suspend fun verifyTotp(accessToken: String, factorId: String, challengeId: String, code: String): AuthTokens {
        calls += "verify:$code"
        verifyError?.let { throw it }
        return tokens("aal2")
    }

    override suspend fun signOut(accessToken: String) { calls += "logout" }
}

class AuthRepositoryTest {
    private var now = 1_000_000L
    private val api = FakeAuthApi()
    private val store = MemoryStore()
    private val repo get() = AuthRepository(api, store) { now }

    @Test
    fun `2段階認証が無ければ、ログインしてすぐ保存される`() = runBlocking {
        val r = repo
        assertEquals(LoginResult.Success, r.signIn(" admin@example.com ", "pw"))
        assertEquals("pw-access-1", r.session.value?.accessToken)
        assertEquals(now + 3600, r.session.value?.expiresAtEpochSec)
        assertEquals(r.session.value, store.saved)
        assertTrue(api.calls.contains("login:admin@example.com")) // 前後の空白は除く
    }

    @Test
    fun `2段階認証が設定済みなら、コード確認までは保存しない`() = runBlocking {
        api.factors = listOf(TotpFactor("f1", verified = true))
        val r = repo
        assertEquals(LoginResult.NeedsTotp("f1"), r.signIn("a@example.com", "pw"))
        assertNull(r.session.value)
        assertNull(store.saved)
        assertEquals(0, store.saveCount)

        assertEquals(LoginResult.Success, r.submitTotp("123 456"))
        assertEquals("aal2-access-2", r.session.value?.accessToken)
        assertEquals(r.session.value, store.saved)
        assertTrue(api.calls.contains("verify:123456"))
    }

    @Test
    fun `未確認(verifiedでない)の認証アプリは無視する`() = runBlocking {
        api.factors = listOf(TotpFactor("f1", verified = false))
        assertEquals(LoginResult.Success, repo.signIn("a@example.com", "pw"))
    }

    @Test
    fun `確認コードが間違っていれば失敗し、保存しない`() = runBlocking {
        api.factors = listOf(TotpFactor("f1", true))
        api.verifyError = AuthApiException("Invalid TOTP code", 400)
        val r = repo
        r.signIn("a@example.com", "pw")
        val result = r.submitTotp("000000")
        assertEquals(LoginResult.Failure("確認コードが正しくありません"), result)
        assertNull(r.session.value)
        assertNull(store.saved)
    }

    @Test
    fun `6桁でないコードはサーバーに送らない`() = runBlocking {
        api.factors = listOf(TotpFactor("f1", true))
        val r = repo
        r.signIn("a@example.com", "pw")
        assertTrue(r.submitTotp("123") is LoginResult.Failure)
        assertTrue(api.calls.none { it.startsWith("verify") })
    }

    @Test
    fun `やり直すと保留中のログインは破棄される`() = runBlocking {
        api.factors = listOf(TotpFactor("f1", true))
        val r = repo
        r.signIn("a@example.com", "pw")
        r.cancelPending()
        assertEquals(LoginResult.Failure("最初からやり直してください"), r.submitTotp("123456"))
    }

    @Test
    fun `パスワードが違うときは分かりやすい日本語にする`() = runBlocking {
        api.loginError = AuthApiException("Invalid login credentials", 400)
        assertEquals(LoginResult.Failure("メールアドレスまたはパスワードが正しくありません"), repo.signIn("a@example.com", "bad"))
        assertNull(store.saved)
    }

    @Test
    fun `BANされたアカウントのログインは、その旨を返す`() = runBlocking {
        api.loginError = AuthApiException("User is banned", 400)
        assertEquals(LoginResult.Failure("このアカウントは利用できません"), repo.signIn("a@example.com", "pw"))
    }

    @Test
    fun `有効期限に余裕があるトークンはそのまま使う`() = runBlocking {
        val r = repo
        r.signIn("a@example.com", "pw")
        now += 1000
        assertEquals("pw-access-1", r.validToken())
        assertTrue(api.calls.none { it.startsWith("refresh") })
    }

    @Test
    fun `期限が近ければ更新してから返し、新しいセッションを保存する`() = runBlocking {
        val r = repo
        r.signIn("a@example.com", "pw")
        now += 3600 - 30 // 残り30秒
        assertEquals("re-access-2", r.validToken())
        assertTrue(api.calls.contains("refresh:pw-refresh-1"))
        assertEquals("re-access-2", store.saved?.accessToken)
    }

    @Test
    fun `更新がサーバーに拒否されたらログアウトする`() = runBlocking {
        val r = repo
        r.signIn("a@example.com", "pw")
        api.refreshError = AuthApiException("Invalid Refresh Token", 400)
        now += 4000
        assertThrows(SessionExpiredException::class.java) { runBlocking { r.validToken() } }
        assertNull(r.session.value)
        assertNull(store.saved)
    }

    @Test
    fun `通信できないだけなら、ログイン状態は維持する`() = runBlocking {
        val r = repo
        r.signIn("a@example.com", "pw")
        api.refreshError = AuthApiException("通信に失敗しました", 0)
        now += 4000
        val e = assertThrows(AuthApiException::class.java) { runBlocking { r.validToken() } }
        assertTrue(e.message!!.contains("通信"))
        assertNotNull(r.session.value)
        assertNotNull(store.saved)
    }

    @Test
    fun `ログインしていなければ SessionExpiredException`() {
        assertThrows(SessionExpiredException::class.java) { runBlocking { repo.validToken() } }
        assertNull(runBlocking { repo.forceRefresh() })
    }

    @Test
    fun `保存されたセッションで起動できる`() = runBlocking {
        store.saved = Session("saved-access", "saved-refresh", now + 3000, "admin-1", "admin@example.com")
        val r = repo
        assertEquals("saved-access", r.validToken())
        assertEquals("admin-1", r.session.value?.userId)
    }

    @Test
    fun `ログアウトすると保存も消え、サーバーにも通知する`() = runBlocking {
        val r = repo
        r.signIn("a@example.com", "pw")
        r.signOut()
        assertNull(r.session.value)
        assertNull(store.saved)
        assertTrue(api.calls.contains("logout"))
    }
}
