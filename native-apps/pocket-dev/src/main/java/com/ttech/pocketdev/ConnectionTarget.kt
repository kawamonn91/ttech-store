package com.ttech.pocketdev

import java.net.URI
import java.net.URLEncoder

/**
 * 接続先サーバー(pocket-dev エージェント)の指定。
 * 入力の正規化だけを担当し、Android に依存しないので単体テストできる。
 */
data class ConnectionTarget(val baseUrl: String, val token: String) {

    /** ターミナル画面。サーバーが Cookie を発行するので、以降の通信はトークン無しで通る */
    val terminalUrl: String get() = "$baseUrl/?token=${encodedToken()}"

    /** IDE 画面 */
    val ideUrl: String get() = "$baseUrl/ide.html?token=${encodedToken()}"

    /** このURLを本アプリ内で開いてよいか(別サイトへの遷移は外部ブラウザに渡す) */
    fun isOwnHost(url: String): Boolean {
        val target = runCatching { URI(url) }.getOrNull() ?: return false
        val mine = runCatching { URI(baseUrl) }.getOrNull() ?: return false
        return target.scheme == mine.scheme &&
            target.host == mine.host &&
            effectivePort(target) == effectivePort(mine)
    }

    private fun encodedToken(): String = URLEncoder.encode(token, "UTF-8")

    companion object {
        const val DEFAULT_PORT = 8765

        /**
         * 入力されたサーバーの場所を `http://host:port` の形に揃える。
         * 形式が不正なら null。
         *
         * 受け付ける例:
         *   192.168.1.10         → http://192.168.1.10:8765
         *   192.168.1.10:9000    → http://192.168.1.10:9000
         *   http://pc.local:8765/ → http://pc.local:8765
         *   https://example.com  → https://example.com:443
         */
        fun normalizeBaseUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            // 末尾のスラッシュは落とさない。"http://" が "http:" に化けて
            // "http://http:" として解釈されてしまうため。
            // パスやクエリは下で scheme/host/port から組み直すので、そのままでよい。
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null

            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null

            // ホスト名の大小文字は区別しない(保存した値と遷移先URLの比較を一致させる)
            val host = uri.host?.lowercase()
            if (host.isNullOrBlank()) return null

            val port = if (uri.port != -1) uri.port else defaultPortFor(scheme)
            if (port !in 1..65535) return null

            return "$scheme://$host:$port"
        }

        private fun defaultPortFor(scheme: String): Int =
            if (scheme == "https") 443 else DEFAULT_PORT

        private fun effectivePort(uri: URI): Int =
            if (uri.port != -1) uri.port else defaultPortFor(uri.scheme?.lowercase() ?: "http")
    }
}
