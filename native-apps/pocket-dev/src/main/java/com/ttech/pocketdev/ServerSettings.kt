package com.ttech.pocketdev

import android.content.Context

/**
 * 接続先の保存。
 * トークンは「自分で立てたサーバーの合鍵」なので、他アプリから読めない
 * アプリ専用領域(MODE_PRIVATE の SharedPreferences)に置く。
 */
class ServerSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pocket-dev", Context.MODE_PRIVATE)

    fun load(): ConnectionTarget? {
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        if (baseUrl.isBlank() || token.isBlank()) return null
        return ConnectionTarget(baseUrl, token)
    }

    fun save(target: ConnectionTarget) {
        prefs.edit()
            .putString(KEY_BASE_URL, target.baseUrl)
            .putString(KEY_TOKEN, target.token)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
    }
}
