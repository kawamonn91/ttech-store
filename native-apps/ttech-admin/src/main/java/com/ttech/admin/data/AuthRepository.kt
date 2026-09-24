package com.ttech.admin.data

import com.ttech.admin.domain.Session
import com.ttech.admin.domain.Totp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 管理APIの呼び出しに、有効なアクセストークンを渡す役 */
interface TokenSource {
    /** 有効期限が近ければ更新して、使えるアクセストークンを返す。ログインしていなければ SessionExpiredException */
    suspend fun validToken(): String

    /** サーバーに拒否されたときに、更新してもう一度試すためのトークン。更新できなければ null(=ログアウト扱い) */
    suspend fun forceRefresh(): String?
}

class SessionExpiredException : Exception("ログインの有効期限が切れました。もう一度ログインしてください")

sealed interface LoginResult {
    data object Success : LoginResult
    data class NeedsTotp(val factorId: String) : LoginResult
    data class Failure(val message: String) : LoginResult
}

interface SessionStore {
    fun load(): Session?
    fun save(session: Session?)
}

/**
 * ログインの状態を持つ。
 *  1. メール+パスワードでログイン
 *  2. 2段階認証(TOTP)が設定されていれば、コード入力までは「保留」にして保存しない
 *     (パスワードだけのトークンでは管理APIが使えず、端末にも残さないため)
 *  3. 完了したら Keystore で暗号化して端末に保存し、次回以降はそのまま開ける
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
    private val nowEpochSec: () -> Long = { System.currentTimeMillis() / 1000 },
) : TokenSource {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val refreshLock = Mutex()
    private var pending: PendingTotp? = null

    private class PendingTotp(val session: Session, val factorId: String)

    suspend fun signIn(email: String, password: String): LoginResult {
        pending = null
        val session = try {
            api.signInWithPassword(email.trim(), password).toSession(nowEpochSec())
        } catch (e: AuthApiException) {
            return LoginResult.Failure(friendly(e))
        }
        val factor = try {
            api.listTotpFactors(session.accessToken).firstOrNull { it.verified }
        } catch (e: AuthApiException) {
            return LoginResult.Failure(friendly(e))
        }
        if (factor == null) {
            finish(session)
            return LoginResult.Success
        }
        pending = PendingTotp(session, factor.id)
        return LoginResult.NeedsTotp(factor.id)
    }

    /** 認証アプリの6桁コードで、保留中のログインを完了する */
    suspend fun submitTotp(code: String): LoginResult {
        val p = pending ?: return LoginResult.Failure("最初からやり直してください")
        if (!Totp.isValid(code)) return LoginResult.Failure("6桁の確認コードを入力してください")
        return try {
            val challengeId = api.challengeTotp(p.session.accessToken, p.factorId)
            val verified = api.verifyTotp(p.session.accessToken, p.factorId, challengeId, Totp.normalize(code))
            pending = null
            finish(verified.toSession(nowEpochSec()))
            LoginResult.Success
        } catch (e: AuthApiException) {
            LoginResult.Failure(if (e.status in 400..422) "確認コードが正しくありません" else friendly(e))
        }
    }

    fun cancelPending() {
        pending = null
    }

    suspend fun signOut() {
        val current = _session.value
        pending = null
        clear()
        if (current != null) api.signOut(current.accessToken)
    }

    override suspend fun validToken(): String {
        val current = _session.value ?: throw SessionExpiredException()
        if (!current.needsRefresh(nowEpochSec())) return current.accessToken
        forceRefresh()?.let { return it }
        // 拒否されて捨てた場合はログアウト。通信できなかっただけなら、ログインは維持したままエラーにする
        if (_session.value == null) throw SessionExpiredException()
        throw AuthApiException("通信に失敗しました。ネットワーク接続を確認してください")
    }

    override suspend fun forceRefresh(): String? = refreshLock.withLock {
        val current = _session.value ?: return null
        try {
            val refreshed = api.refresh(current.refreshToken).toSession(nowEpochSec())
            finish(refreshed)
            refreshed.accessToken
        } catch (e: AuthApiException) {
            // 通信できないだけなら、ログイン状態は維持する(通信が戻ればまた使える)。拒否されたときだけ捨てる
            if (e.status in 400..499) clear()
            null
        }
    }

    private fun finish(session: Session) {
        store.save(session)
        _session.value = session
    }

    private fun clear() {
        store.save(null)
        _session.value = null
    }

    private fun friendly(e: AuthApiException): String = when {
        e.message?.contains("Invalid login credentials", ignoreCase = true) == true -> "メールアドレスまたはパスワードが正しくありません"
        e.message?.contains("banned", ignoreCase = true) == true -> "このアカウントは利用できません"
        else -> e.message ?: "ログインに失敗しました"
    }
}
