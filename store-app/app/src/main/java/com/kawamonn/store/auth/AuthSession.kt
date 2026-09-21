package com.kawamonn.store.auth

import android.content.Context

/** 認証トークンの端末内保存。テストで差し替えられるよう、実装(SharedPreferences)と切り離す */
interface AuthSession {
    data class Stored(val accessToken: String, val refreshToken: String, val userId: String, val email: String?)

    fun load(): Stored?
    fun save(session: AuthSessionData)
    fun clear()
}

/**
 * 現状は他の設定値と同じ SharedPreferences を使っている(このアプリの既存の方針に合わせた)。
 * トークンという性質上、将来的には EncryptedSharedPreferences 等への強化が望ましい。
 */
class SharedPreferencesAuthSession(context: Context) : AuthSession {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    override fun load(): AuthSession.Stored? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return AuthSession.Stored(access, refresh, userId, prefs.getString(KEY_EMAIL, null))
    }

    override fun save(session: AuthSessionData) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_EMAIL, session.user.email)
            .apply()
    }

    override fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
