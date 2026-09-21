package com.kawamonn.store.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
)

@Serializable
data class AuthSessionData(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: AuthUser,
)

@Serializable
data class MfaFactor(
    val id: String,
    @SerialName("factor_type") val factorType: String,
    val status: String,
)

class AuthApiException(message: String) : java.io.IOException(message)

/** 管理API側が2段階認証(aal2)を要求しているときに投げる。ストアアプリはこれを受けてTOTPコード入力を促す */
class TotpRequiredException : Exception("2段階認証が必要です")

/** Supabase Auth(GoTrue)への操作。テストで差し替えられるよう、実装(REST呼び出し)と切り離す */
interface AuthApi {
    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String): AuthSessionData
    suspend fun signInWithPassword(email: String, password: String): AuthSessionData
    suspend fun refresh(refreshToken: String): AuthSessionData
    suspend fun signOut(accessToken: String)

    /** 現在のユーザーに設定されているMFA要素の一覧(TOTPの有無・検証済みかを見るのに使う) */
    suspend fun listFactors(accessToken: String): List<MfaFactor>

    /** TOTPの検証チャレンジを開始し、challenge_id を返す */
    suspend fun challengeTotp(accessToken: String, factorId: String): String

    /** チャレンジに対して6桁コードを検証し、aal2に昇格した新しいセッションを返す */
    suspend fun verifyTotp(accessToken: String, factorId: String, challengeId: String, code: String): AuthSessionData
}
