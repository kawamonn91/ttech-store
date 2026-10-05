package com.kawamonn.store.auth

import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?, val accessToken: String) : AuthState
}

/**
 * ログイン状態を保持し、Google IDトークン/メール・パスワードでのサインインをまとめる。
 * アクセストークンは約1時間で切れるため、認証が要る呼び出しの前に [ensureFreshToken] を通すこと。
 */
class AuthRepository(
    private val api: AuthApi,
    private val session: AuthSession,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** リフレッシュトークンは一度しか使えないため、同時に更新させない(後から来た呼び出しは、先の更新結果を使う) */
    private val refreshMutex = Mutex()

    init {
        session.load()?.let {
            _state.value = AuthState.SignedIn(it.userId, it.email, it.accessToken)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String): Result<Unit> = runCatching {
        applySession(api.signInWithGoogleIdToken(idToken, nonce))
    }

    suspend fun signInWithPassword(email: String, password: String): Result<Unit> = runCatching {
        applySession(api.signInWithPassword(email, password))
    }

    /**
     * 期限が近い(または切れている)アクセストークンは、リフレッシュトークンで更新してから返す。
     * 未ログインなら null。更新に失敗したときは、保存済みのトークンをそのまま返す(呼び出し側のAPIがエラーを返す)。
     */
    suspend fun ensureFreshToken(): String? = refreshMutex.withLock {
        val stored = session.load() ?: return@withLock null
        if (!isExpiringSoon(stored.accessToken, System.currentTimeMillis())) return@withLock stored.accessToken
        val refreshed = runCatching { api.refresh(stored.refreshToken) }.getOrNull() ?: return@withLock stored.accessToken
        applySession(refreshed)
        refreshed.accessToken
    }

    /**
     * 管理者操作でサーバーから[TotpRequiredException]を受け取ったときに呼ぶ。
     * TOTPコードを検証し、成功したらaal2(2段階認証済み)に昇格した新しいセッションを保存する。
     * 呼び出し側はこの後、失敗した操作を再試行すればよい。
     */
    suspend fun verifyTotpCode(code: String): Result<Unit> = runCatching {
        val token = ensureFreshToken() ?: throw AuthApiException("ログインしてください")
        val factor = api.listFactors(token).firstOrNull { it.factorType == "totp" && it.status == "verified" }
            ?: throw AuthApiException("2段階認証が設定されていません")
        val challengeId = api.challengeTotp(token, factor.id)
        applySession(api.verifyTotp(token, factor.id, challengeId, code))
    }

    fun signOut() {
        val token = (state.value as? AuthState.SignedIn)?.accessToken
        session.clear()
        _state.value = AuthState.SignedOut
        if (token != null) scope.launch { api.signOut(token) }
    }

    private fun applySession(newSession: AuthSessionData) {
        session.save(newSession)
        _state.value = AuthState.SignedIn(newSession.user.id, newSession.user.email, newSession.accessToken)
    }
}

/** JWTの有効期限(exp)まで残り1分を切っていれば、更新が必要とみなす。読めないトークンは更新しない */
internal fun isExpiringSoon(accessToken: String, nowMs: Long): Boolean {
    val exp = runCatching {
        val payload = accessToken.split(".")[1]
        val json = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)).jsonObject
        json.getValue("exp").jsonPrimitive.long
    }.getOrNull() ?: return false
    return exp - nowMs / 1000 < 60
}
