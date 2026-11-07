package com.ttech.pocketdev.drive

import android.util.Log
import com.ttech.pocketdev.bridge.randomToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** rclone の API が返したエラー（[message] は rclone の英語の文のまま） */
class RcException(val status: Int, message: String) : IOException(message)

/** 読もうとしたファイルが [limit] バイトより大きい */
class TooLargeException(val limit: Long) : IOException("too large")

/**
 * `rclone rcd`（遠隔操作の API）を端末内（127.0.0.1）で動かし、HTTP で操作を頼む。
 *
 * 1回ごとに rclone を起動するより速い（フォルダの ID などを覚えておける）。
 * 同じ端末のほかのアプリも 127.0.0.1 にはつなげるので、起動ごとに作る合言葉（Basic 認証）を付ける。
 * ファイルの中身は `--rc-serve`（`GET /[gdrive:]/パス`）で読む。
 */
class RcloneDaemon(private val rclone: Rclone) : Closeable {

    private val password = randomToken()
    private val authorization = "Basic " + Base64.getEncoder().encodeToString("$USER:$password".toByteArray())
    private val recentLog = ArrayDeque<String>()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var baseUrl = ""

    @Volatile
    private var closed = false

    val isRunning: Boolean get() = process?.isAlive == true

    /** 起動していなければ起動して、API が答えるまで待つ（時間がかかるので、メインスレッドで呼ばない） */
    @Synchronized
    fun ensureStarted(timeoutMs: Long = START_TIMEOUT_MS) {
        // 閉じたあとに起動すると、誰も止められないプロセスが残る
        if (closed) throw IOException("rclone は止められています。開き直してください")
        if (isRunning) return
        stopProcess()
        val port = ServerSocket(0, 1, InetAddress.getByName(HOST)).use { it.localPort }
        baseUrl = "http://$HOST:$port"
        val started = rclone.processBuilder(
            listOf(
                "rcd",
                "--rc-addr", "$HOST:$port",
                "--rc-serve",
                // Google ドキュメントなどは中身が文書ファイルではないので、一覧に出さない
                "--drive-skip-gdocs",
                // 電波が弱いときに、IDE が待ち続けないようにする
                "--contimeout", "20s",
                "--timeout", "2m",
                "--low-level-retries", "3",
                "-v",
            ),
            mapOf("RCLONE_RC_USER" to USER, "RCLONE_RC_PASS" to password),
        ).redirectErrorStream(true).start()
        process = started
        started.outputStream.close()
        thread(name = "rclone-rcd-log", isDaemon = true) {
            runCatching {
                started.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(recentLog) {
                        recentLog.addLast(line)
                        while (recentLog.size > 20) recentLog.removeFirst()
                    }
                    Log.d(TAG, line)
                }
            }
        }

        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            if (!started.isAlive) {
                stopProcess()
                throw IOException("rclone が起動しませんでした: ${lastLogLine()}")
            }
            if (runCatching { call("rc/noop") }.isSuccess) return
            if (System.nanoTime() > deadline) {
                stopProcess()
                throw IOException("rclone の起動を待ちきれませんでした")
            }
            Thread.sleep(150)
        }
    }

    /** API を呼ぶ（POST・JSON）。失敗は [RcException] */
    fun call(method: String, params: JsonObject = JsonObject(emptyMap())): JsonObject {
        val connection = open("$baseUrl/$method")
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        val body = params.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
        return readJson(connection)
    }

    /** [dir]（Drive の中のフォルダ）に [name] というファイルを、[data] の中身で作るか書き換える */
    fun upload(dir: String, name: String, data: ByteArray) {
        val boundary = "pocketdev-" + randomToken()
        val head = "--$boundary\r\n" +
            // 日本語の名前もそのまま渡せるよう、RFC 2231 の形（filename*=UTF-8''…）で書く（Go が読める）
            "Content-Disposition: form-data; name=\"file\"; filename*=UTF-8''${percentEncode(name)}\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
        val tail = "\r\n--$boundary--\r\n"
        val headBytes = head.toByteArray(Charsets.US_ASCII)
        val tailBytes = tail.toByteArray(Charsets.US_ASCII)

        val query = "fs=" + URLEncoder.encode(Rclone.FS, "UTF-8") + "&remote=" + URLEncoder.encode(dir, "UTF-8")
        val connection = open("$baseUrl/operations/uploadfile?$query")
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setFixedLengthStreamingMode(headBytes.size + data.size + tailBytes.size)
        connection.outputStream.use {
            it.write(headBytes)
            it.write(data)
            it.write(tailBytes)
        }
        readJson(connection)
    }

    /** Drive の中のパス [remote] のファイルの中身を読む。[maxBytes] を超えたら [TooLargeException] */
    fun download(remote: String, maxBytes: Long): ByteArray {
        val path = remote.split('/').joinToString("/") { percentEncode(it) }
        val connection = open("$baseUrl/${percentEncode("[${Rclone.FS}]")}/$path")
        connection.requestMethod = "GET"
        val code = connection.responseCode
        if (code !in 200..299) {
            readJson(connection)
            throw RcException(code, "HTTP $code")
        }
        val length = connection.contentLengthLong
        if (length > maxBytes) {
            connection.disconnect()
            throw TooLargeException(maxBytes)
        }
        return connection.inputStream.use { readLimited(it, maxBytes) }
    }

    override fun close() {
        closed = true
        stopProcess()
    }

    @Synchronized
    private fun stopProcess() {
        val running = process ?: return
        process = null
        running.destroy()
        if (!running.waitFor(2, TimeUnit.SECONDS)) running.destroyForcibly()
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Authorization", authorization)
        }

    private fun readJson(connection: HttpURLConnection): JsonObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (code !in 200..299) {
            val message = obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "HTTP $code"
            throw RcException(code, message)
        }
        return obj ?: JsonObject(emptyMap())
    }

    private fun lastLogLine(): String = synchronized(recentLog) {
        recentLog.lastOrNull { it.contains("ERROR") || it.contains("CRITICAL") } ?: recentLog.lastOrNull().orEmpty()
    }

    companion object {
        private const val TAG = "RcloneDaemon"
        private const val HOST = "127.0.0.1"
        private const val USER = "pocketdev"
        private const val START_TIMEOUT_MS = 20_000L
        private const val READ_TIMEOUT_MS = 180_000

        /** URL のパスや RFC 2231 の値に使う。英数字と「-._~」以外は %XX にする（空白は %20） */
        fun percentEncode(value: String): String {
            val out = StringBuilder()
            for (b in value.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xff
                if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                    c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
                ) {
                    out.append(c.toChar())
                } else {
                    out.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xf])
                }
            }
            return out.toString()
        }

        /** [limit] バイトまで読む。超えたら [TooLargeException] */
        fun readLimited(input: InputStream, limit: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) throw TooLargeException(limit)
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}
