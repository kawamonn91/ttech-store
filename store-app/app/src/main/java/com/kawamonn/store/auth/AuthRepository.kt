package com.kawamonn.store.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?, val accessToken: String) : AuthState
}

/**
 * ログイン状態を保持し、Google IDトークン/メール・パスワードでのサインインをまとめる。
 * アクセストークンは短命なので、使う側は都度 [currentAccessToken] を呼んで
 * 必要ならリフレッシュしてから使うこと。
 */
class AuthRepository(
    private val api: AuthApi,
    private val session: AuthSession,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var refreshToken: String? = null

    init {
        session.load()?.let {
            refreshToken = it.refreshToken
            _state.value = AuthState.SignedIn(it.userId, it.email, it.accessToken)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String): Result<Unit> = runCatching {
        applySession(api.signInWithGoogleIdToken(idToken, nonce))
    }

    suspend fun signInWithPassword(email: String, password: String): Result<Unit> = runCatching {
        applySession(api.signInWithPassword(email, password))
    }

    /** 今のアクセストークンを返す。未ログインなら null(期限切れの追跡はせず、401時に [refreshAndRetry] を呼ぶ方式) */
    suspend fun currentAccessToken(): String? = session.load()?.accessToken

    /**
     * 管理者操作でサーバーから[TotpRequiredException]を受け取ったときに呼ぶ。
     * TOTPコードを検証し、成功したらaal2(2段階認証済み)に昇格した新しいセッションを保存する。
     * 呼び出し側はこの後、失敗した操作を再試行すればよい。
     */
    suspend fun verifyTotpCode(code: String): Result<Unit> = runCatching {
        val token = currentAccessToken() ?: throw AuthApiException("ログインしてください")
        val factor = api.listFactors(token).firstOrNull { it.factorType == "totp" && it.status == "verified" }
            ?: throw AuthApiException("2段階認証が設定されていません")
        val challengeId = api.challengeTotp(token, factor.id)
        applySession(api.verifyTotp(token, factor.id, challengeId, code))
    }

    suspend fun refreshAndRetry(): String? {
        val token = refreshToken ?: return null
        return runCatching { applySession(api.refresh(token)); session.load()?.accessToken }.getOrNull()
    }

    fun signOut() {
        val token = (state.value as? AuthState.SignedIn)?.accessToken
        session.clear()
        refreshToken = null
        _state.value = AuthState.SignedOut
        if (token != null) scope.launch { api.signOut(token) }
    }

    private fun applySession(newSession: AuthSessionData) {
        session.save(newSession)
        refreshToken = newSession.refreshToken
        _state.value = AuthState.SignedIn(newSession.user.id, newSession.user.email, newSession.accessToken)
    }
}
