package com.ttech.pocketdev.drive

import android.content.Context
import com.ttech.pocketdev.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google のログインに使う OAuth クライアント。build.gradle.kts が google-oauth.properties から
 * BuildConfig に入れる（rclone 共有のクライアントは 2026 年中に使えなくなるので使わない）。
 * 秘密鍵がログに出ないよう、data class にしない。
 */
class DriveClient(val id: String, val secret: String) {
    val configured: Boolean get() = id.isNotBlank() && secret.isNotBlank()

    companion object {
        val BUILT_IN: DriveClient get() = DriveClient(BuildConfig.DRIVE_CLIENT_ID, BuildConfig.DRIVE_CLIENT_SECRET)
    }
}

/**
 * 同梱の rclone（scripts/build-rclone.sh で作る Linux 版）を、アプリのプロセスから直接動かす。
 *
 * Pocket Linux と同じく、Android 上でそのまま動かすため、名前解決は [ConnectProxy]（HTTPS_PROXY）、
 * 証明書は Android の証明書フォルダ（SSL_CERT_DIR）で解決する。Google のログインは [DriveClient] を使う。
 * ログインの情報は、アプリ専用の領域の rclone.conf に置く（rclone がトークンを更新すると書き直す）。
 */
class Rclone(context: Context, private val proxy: ConnectProxy, private val client: DriveClient = DriveClient.BUILT_IN) {

    private val executable = File(context.applicationInfo.nativeLibraryDir, "librclone.so")
    private val dir = File(context.filesDir, "rclone")
    private val cacheDir = File(context.cacheDir, "rclone")
    private val configFile = File(dir, "rclone.conf")

    /** この端末の CPU 向けの rclone が入っているか（arm64-v8a と x86_64 だけ同梱している） */
    val available: Boolean get() = executable.isFile

    /** このビルドに Google のログインの設定が入っているか */
    val configured: Boolean get() = client.configured

    val isLoggedIn: Boolean
        get() = configFile.isFile && isLoggedInWith(runCatching { configFile.readLines() }.getOrDefault(emptyList()), client)

    fun processBuilder(args: List<String>, extraEnv: Map<String, String> = emptyMap()): ProcessBuilder {
        dir.mkdirs()
        val tmp = File(cacheDir, "tmp").apply { mkdirs() }
        return ProcessBuilder(listOf(executable.path) + args).directory(dir).apply {
            environment().apply {
                clear()
                put("HOME", dir.path)
                put("PATH", "/system/bin")
                put("TMPDIR", tmp.path)
                put("RCLONE_CONFIG", configFile.path)
                put("RCLONE_CACHE_DIR", File(cacheDir, "cache").path)
                put("HTTPS_PROXY", proxy.url)
                put("NO_PROXY", "127.0.0.1,localhost")
                put("SSL_CERT_DIR", CERT_DIRS)
                putAll(extraEnv)
            }
        }
    }

    /**
     * Google でログインする。同意画面の URL を [onUrl] で知らせ（画面がブラウザで開く）、
     * 同意が終わると rclone が受け取ったトークンを設定ファイルに書く。トークンはログに出さない。
     * コルーチンが取り消されたら rclone も止める。
     */
    suspend fun login(onUrl: (String) -> Unit, log: (String) -> Unit) {
        if (!client.configured) throw IOException(NOT_CONFIGURED)
        val output = StringBuilder()
        var opened = false
        var inToken = false
        val exit = runLines(authorizeArgs(client)) { line ->
            if (!opened) AUTH_URL.find(line)?.let { opened = true; onUrl(it.value) }
            output.appendLine(line)
            when {
                line.contains(TOKEN_BEGIN) -> inToken = true
                line.contains(TOKEN_END) -> inToken = false
                !inToken -> log(line)
            }
        }
        val token = parseToken(output.toString())
            ?: throw IOException(loginError(output.toString()) ?: "ログインできませんでした（終了コード $exit）")
        writeConfig(token)
    }

    fun logout() {
        configFile.delete()
    }

    private fun writeConfig(token: String) {
        dir.mkdirs()
        val tmp = File(configFile.path + ".tmp")
        tmp.writeText(configText(client, token))
        if (!tmp.renameTo(configFile)) {
            configFile.delete()
            tmp.renameTo(configFile)
        }
    }

    /** 標準出力と標準エラーを1行ずつ受け取りながら動かし、終了コードを返す */
    private suspend fun runLines(args: List<String>, onLine: (String) -> Unit): Int = coroutineScope {
        val process = processBuilder(args).redirectErrorStream(true).start()
        // 読み込み待ちのまま取り消されても、プロセスを止めれば読み込みが EOF で抜ける。
        // 止まるまで待つのは、次のログインが同じポート（53682）を使うため
        val killer = launch {
            try {
                awaitCancellation()
            } finally {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                process.outputStream.close()
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
                process.waitFor()
            }.also { ensureActive() }
        } finally {
            killer.cancel()
        }
    }

    companion object {
        const val REMOTE = "gdrive"

        /** rcd などに渡す、マイドライブを指す名前 */
        const val FS = "$REMOTE:"

        /** Android 14 からは証明書が Conscrypt の APEX にある。古い場所も並べる（Go は「:」区切りで全部読む） */
        const val CERT_DIRS = "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts"

        private val AUTH_URL = Regex("""http://127\.0\.0\.1:53682/auth\?state=[A-Za-z0-9_\-]+""")
        private const val TOKEN_BEGIN = "--->"
        private const val TOKEN_END = "<---End paste"
        private val json = Json { ignoreUnknownKeys = true }

        const val NOT_CONFIGURED = "この版には Google へのログインの設定が入っていません"

        fun authorizeArgs(client: DriveClient): List<String> =
            listOf("authorize", "drive", client.id, client.secret, "--auth-no-open-browser")

        fun configText(client: DriveClient, token: String): String =
            "[$REMOTE]\ntype = drive\nscope = drive\nclient_id = ${client.id}\nclient_secret = ${client.secret}\ntoken = $token\n"

        /**
         * rclone.conf に、今のクライアントでログインした情報があるか。
         * トークンは取ったクライアントでしか更新できないので、別のクライアントのものはログインし直してもらう
         */
        fun isLoggedInWith(lines: List<String>, client: DriveClient): Boolean {
            val trimmed = lines.map { it.trim() }
            return "[$REMOTE]" in trimmed && "client_id = ${client.id}" in trimmed
        }

        /** `rclone authorize` の出力から、貼り付け用のトークン（JSON）を取り出す */
        fun parseToken(output: String): String? {
            val body = output.substringAfter(TOKEN_BEGIN, "").substringBefore(TOKEN_END, "")
            val token = body.trim().takeIf { it.startsWith("{") && it.endsWith("}") } ?: return null
            return token.takeIf { runCatching { json.parseToJsonElement(it).jsonObject["access_token"] }.getOrNull() != null }
        }

        /** ログインに失敗したときの出力から、利用者に見せる理由を作る */
        fun loginError(output: String): String? = when {
            output.contains("address already in use") ->
                "ほかのアプリ（Pocket Linux など）が Google のログインの途中です。そちらを終えてからやり直してください"
            output.contains("access_denied") -> "Google で許可されませんでした"
            output.contains("invalid_client") || output.contains("deleted_client") ->
                "この版のログインの設定は使えなくなっています。アプリを新しい版に更新してください"
            else -> null
        }
    }
}
