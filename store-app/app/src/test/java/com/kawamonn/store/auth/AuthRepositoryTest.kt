package com.kawamonn.store.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun sessionData(accessToken: String, refreshToken: String = "rt") = AuthSessionData(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresIn = 3600,
    user = AuthUser(id = "u1", email = "a@example.com"),
)

private fun jwtExpiringAt(expSeconds: Long): String {
    val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":$expSeconds}""".toByteArray())
    return "eyJhbGciOiJIUzI1NiJ9.$payload.signature"
}

/** 実際のREST通信をしない、テスト用の手作りFake(このプロジェクトの既存の方針: モックライブラリを増やさない) */
private class FakeAuthApi : AuthApi {
    var passwordResult: Result<AuthSessionData> = Result.success(sessionData("at1"))
    var signOutCalledWith: String? = null
    var factors: List<MfaFactor> = emptyList()
    var challengeId: String = "challenge-1"
    var verifyResult: Result<AuthSessionData> = Result.success(sessionData("at2"))
    var verifyCalledWith: Triple<String, String, String>? = null // factorId, challengeId, code

    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String) = passwordResult.getOrThrow()
    override suspend fun signInWithPassword(email: String, password: String) = passwordResult.getOrThrow()
    override suspend fun refresh(refreshToken: String) = passwordResult.getOrThrow()
    override suspend fun signOut(accessToken: String) {
        signOutCalledWith = accessToken
    }

    override suspend fun listFactors(accessToken: String): List<MfaFactor> = factors
    override suspend fun challengeTotp(accessToken: String, factorId: String): String = challengeId
    override suspend fun verifyTotp(accessToken: String, factorId: String, challengeId: String, code: String): AuthSessionData {
        verifyCalledWith = Triple(factorId, challengeId, code)
        return verifyResult.getOrThrow()
    }
}

private class FakeAuthSession : AuthSession {
    var stored: AuthSession.Stored? = null
    override fun load() = stored
    override fun save(session: AuthSessionData) {
        stored = AuthSession.Stored(session.accessToken, session.refreshToken, session.user.id, session.user.email)
    }
    override fun clear() {
        stored = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `初期状態は保存済みセッションがあればSignedIn`() {
        val session = FakeAuthSession().apply { stored = AuthSession.Stored("at", "rt", "u1", "a@example.com") }
        val repo = AuthRepository(FakeAuthApi(), session, scope)
        assertTrue(repo.state.value is AuthState.SignedIn)
        assertEquals("u1", (repo.state.value as AuthState.SignedIn).userId)
    }

    @Test
    fun `保存済みセッションが無ければSignedOut`() {
        val repo = AuthRepository(FakeAuthApi(), FakeAuthSession(), scope)
        assertEquals(AuthState.SignedOut, repo.state.value)
    }

    @Test
    fun `パスワードサインインに成功したらSignedInになりセッションを保存する`() {
        val store = FakeAuthSession()
        val repo = AuthRepository(FakeAuthApi().apply { passwordResult = Result.success(sessionData("at1")) }, store, scope)

        val result = scope.runBlockingTest { repo.signInWithPassword("a@example.com", "pw") }

        assertTrue(result.isSuccess)
        assertEquals("at1", store.stored?.accessToken)
        assertEquals("u1", (repo.state.value as AuthState.SignedIn).userId)
    }

    @Test
    fun `サインインに失敗したらSignedOutのまま`() {
        val api = FakeAuthApi().apply { passwordResult = Result.failure(AuthApiException("bad creds")) }
        val repo = AuthRepository(api, FakeAuthSession(), scope)

        val result = scope.runBlockingTest { repo.signInWithPassword("a@example.com", "wrong") }

        assertTrue(result.isFailure)
        assertEquals(AuthState.SignedOut, repo.state.value)
    }

    @Test
    fun `signOutはローカルのセッションを即座に消す`() {
        val store = FakeAuthSession().apply { stored = AuthSession.Stored("at", "rt", "u1", "a@example.com") }
        val repo = AuthRepository(FakeAuthApi(), store, scope)

        repo.signOut()
        scope.advanceUntilIdle()

        assertEquals(AuthState.SignedOut, repo.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `期限に余裕があるトークンはそのまま返し、更新しない`() {
        val fresh = jwtExpiringAt(System.currentTimeMillis() / 1000 + 3600)
        val store = FakeAuthSession().apply { stored = AuthSession.Stored(fresh, "rt", "u1", null) }
        val api = FakeAuthApi().apply { passwordResult = Result.success(sessionData("refreshed")) }
        val repo = AuthRepository(api, store, scope)

        assertEquals(fresh, scope.runBlockingTest { repo.ensureFreshToken() })
        assertEquals(fresh, store.stored?.accessToken)
    }

    @Test
    fun `期限が切れたトークンは更新して、新しいトークンを保存する`() {
        val expired = jwtExpiringAt(System.currentTimeMillis() / 1000 - 10)
        val store = FakeAuthSession().apply { stored = AuthSession.Stored(expired, "rt", "u1", null) }
        val api = FakeAuthApi().apply { passwordResult = Result.success(sessionData("refreshed")) }
        val repo = AuthRepository(api, store, scope)

        assertEquals("refreshed", scope.runBlockingTest { repo.ensureFreshToken() })
        assertEquals("refreshed", store.stored?.accessToken)
        assertEquals("refreshed", (repo.state.value as AuthState.SignedIn).accessToken)
    }

    @Test
    fun `更新に失敗したら保存済みのトークンをそのまま返す`() {
        val expired = jwtExpiringAt(System.currentTimeMillis() / 1000 - 10)
        val store = FakeAuthSession().apply { stored = AuthSession.Stored(expired, "rt", "u1", null) }
        val api = FakeAuthApi().apply { passwordResult = Result.failure(AuthApiException("network")) }
        val repo = AuthRepository(api, store, scope)

        assertEquals(expired, scope.runBlockingTest { repo.ensureFreshToken() })
    }

    @Test
    fun `未ログインならensureFreshTokenはnull`() {
        val repo = AuthRepository(FakeAuthApi(), FakeAuthSession(), scope)
        assertNull(scope.runBlockingTest { repo.ensureFreshToken() })
    }

    @Test
    fun `verifyTotpCodeは検証済みTOTP要素でチャレンジしセッションを更新する`() {
        val store = FakeAuthSession().apply { stored = AuthSession.Stored("at1", "rt", "u1", "a@example.com") }
        val api = FakeAuthApi().apply {
            factors = listOf(MfaFactor(id = "factor-1", factorType = "totp", status = "verified"))
            verifyResult = Result.success(sessionData("at2-aal2"))
        }
        val repo = AuthRepository(api, store, scope)

        val result = scope.runBlockingTest { repo.verifyTotpCode("123456") }

        assertTrue(result.isSuccess)
        assertEquals(Triple("factor-1", "challenge-1", "123456"), api.verifyCalledWith)
        assertEquals("at2-aal2", store.stored?.accessToken)
    }

    @Test
    fun `検証済みTOTP要素が無ければverifyTotpCodeは失敗する`() {
        val store = FakeAuthSession().apply { stored = AuthSession.Stored("at1", "rt", "u1", "a@example.com") }
        val repo = AuthRepository(FakeAuthApi(), store, scope)

        val result = scope.runBlockingTest { repo.verifyTotpCode("123456") }

        assertTrue(result.isFailure)
    }

    /** kotlinx-coroutines-test の runTest はメンバー拡張が使いにくいため、小さなヘルパーで包む */
    private fun <T> TestScope.runBlockingTest(block: suspend () -> T): T {
        var result: T? = null
        launch { result = block() }
        advanceUntilIdle()
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
