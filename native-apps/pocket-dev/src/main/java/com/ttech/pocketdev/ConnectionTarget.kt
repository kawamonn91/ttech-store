package com.ttech.pocketdev

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

/**
 * 接続先の種類。
 */
@Serializable
enum class TargetType {
    /** 同一端末内の Pocket Linux (127.0.0.1) */
    POCKET_LINUX,
    /** PC や VPS など独自に起動した pocket-dev サーバー */
    CUSTOM_SERVER,
    /** SSH 経由（SFTP + PTY） */
    SSH,
    /** Google Drive 直結（編集のみ） */
    GOOGLE_DRIVE,
}

/**
 * 接続先サーバー(pocket-dev エージェント)の指定。
 * 入力の正規化だけを担当し、Android に依存しないので単体テストできる。
 */
@Serializable
data class ConnectionTarget(
    val baseUrl: String = "",
    val token: String = "",
    val name: String = "",
    val type: TargetType = TargetType.CUSTOM_SERVER,
    val id: String = UUID.randomUUID().toString(),

    // SSH 接続用パラメータ
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "root",
    val sshAuthMethod: SshAuthMethod = SshAuthMethod.PASSWORD,
    val sshPassword: String = "",
    val sshPrivateKey: String = "",
    val sshPassphrase: String = "",
    val sshRemoteRoot: String = "",
) {
    @Serializable
    enum class SshAuthMethod {
        PASSWORD,
        KEY,
    }

    /** ターミナル画面。サーバーが Cookie を発行するので、以降の通信はトークン無しで通る */
    val terminalUrl: String get() = "$baseUrl/?token=${encodedToken()}"

    /** IDE 画面 */
    val ideUrl: String get() = "$baseUrl/ide.html?token=${encodedToken()}"

    /** 表示用の名前。未指定の場合は baseUrl または種別に応じた既定名 */
    val displayName: String get() = when {
        name.isNotBlank() -> name
        type == TargetType.POCKET_LINUX -> "Pocket Linux ($baseUrl)"
        type == TargetType.SSH -> "SSH ($sshUser@$sshHost:$sshPort)"
        type == TargetType.GOOGLE_DRIVE -> "Google Drive"
        else -> baseUrl
    }

    /** このURLを本アプリ内で開いてよいか(別サイトへの遷移は外部ブラウザに渡す) */
    fun isOwnHost(url: String): Boolean {
        if (baseUrl.isBlank()) return false
        val target = runCatching { URI(url) }.getOrNull() ?: return false
        val mine = runCatching { URI(baseUrl) }.getOrNull() ?: return false
        return target.scheme == mine.scheme &&
            target.host == mine.host &&
            effectivePort(target) == effectivePort(mine)
    }

    fun toJson(): String = jsonFormat.encodeToString(this)

    private fun encodedToken(): String = URLEncoder.encode(token, "UTF-8")

    companion object {
        const val DEFAULT_PORT = 8765
        const val POCKET_LINUX_PACKAGE = "com.ttech.pocketlinux"
        const val DEFAULT_LOCAL_HOST = "127.0.0.1"

        private val jsonFormat = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** 同一端末の Pocket Linux 向けターゲットを作る */
        fun pocketLinux(token: String, port: Int = DEFAULT_PORT): ConnectionTarget {
            val validPort = if (port in 1..65535) port else DEFAULT_PORT
            return ConnectionTarget(
                baseUrl = "http://$DEFAULT_LOCAL_HOST:$validPort",
                token = token.trim(),
                name = "Pocket Linux",
                type = TargetType.POCKET_LINUX,
            )
        }

        /** SSH ターゲットを作る */
        fun ssh(
            host: String,
            port: Int = 22,
            user: String = "root",
            authMethod: SshAuthMethod = SshAuthMethod.PASSWORD,
            password: String = "",
            privateKey: String = "",
            passphrase: String = "",
            remoteRoot: String = "",
            name: String = "",
        ): ConnectionTarget {
            val validPort = if (port in 1..65535) port else 22
            val cleanHost = host.trim()
            val cleanUser = user.trim().ifBlank { "root" }
            return ConnectionTarget(
                baseUrl = "", // ローカルブリッジサーバー起動時に設定される
                token = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "SSH: $cleanUser@$cleanHost" },
                type = TargetType.SSH,
                sshHost = cleanHost,
                sshPort = validPort,
                sshUser = cleanUser,
                sshAuthMethod = authMethod,
                sshPassword = password,
                sshPrivateKey = privateKey.trim(),
                sshPassphrase = passphrase,
                sshRemoteRoot = remoteRoot.trim(),
            )
        }

        fun fromJson(json: String): ConnectionTarget? = runCatching {
            jsonFormat.decodeFromString<ConnectionTarget>(json)
        }.getOrNull()

        fun listToJson(list: List<ConnectionTarget>): String =
            jsonFormat.encodeToString(list)

        fun listFromJson(json: String): List<ConnectionTarget> = runCatching {
            jsonFormat.decodeFromString<List<ConnectionTarget>>(json)
        }.getOrDefault(emptyList())

        /**
         * 入力されたサーバーの場所を `http://host:port` の形に揃える。
         * 形式が不正なら null。
         */
        fun normalizeBaseUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null

            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null

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
