package com.ttech.pocketdev.bridge

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * アプリ内（127.0.0.1）で動かし、WebView の IDE・ターミナル（同梱の web/）に、
 * pocket-dev のサーバー（agent/server.py）と同じ API を出す中継サーバー。
 * ファイルの操作は [files]（SSH・Google ドライブ）に、ターミナルは [pty] に任せる。
 * [pty] が無い接続先では `/api/info` の terminal を false にし、IDE に実行のボタンを隠させる。
 *
 * 同じ端末のほかのアプリからも 127.0.0.1 にはつなげるので、`/health` 以外は
 * WebSocket も含めてすべてトークン（URL の token か Cookie）を確かめる。
 */
class BridgeServer(
    private val token: String,
    private val files: FileBackend,
    private val pty: PtyBackend?,
    /** 同梱の web/ のファイルを開く（無ければ null）。引数は "ide.html" などの名前 */
    private val openAsset: (String) -> InputStream?,
    port: Int = 0,
) : NanoWSD(LOCALHOST, port) {

    /** 待ち受けを始める。WebSocket は入力が無い間も切らないよう、読み込みの時間切れを無くす */
    fun startServer() {
        start(0, true)
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri == "/health") {
            return json(200, buildJsonObject { put("ok", true); put("service", "pocket-dev-bridge") })
        }
        val viaUrl = matches(session.parameters["token"]?.firstOrNull())
        if (!viaUrl && !matches(cookieValue(session.headers["cookie"], COOKIE_NAME))) {
            drainBody(session)
            return error(401, "認証トークンが必要です")
        }
        if (isWebsocketRequested(session)) {
            if (pty == null || session.uri != "/ws/pty") return error(404, "この接続先ではターミナルを使えません")
            return super.serve(session)
        }
        return try {
            handleHttp(session).also { if (viaUrl) it.addHeader("Set-Cookie", cookieHeader()) }
        } catch (e: BridgeException) {
            error(e.status, e.message ?: "エラー")
        } catch (e: Exception) {
            error(500, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun handleHttp(session: IHTTPSession): Response {
        val uri = session.uri
        if (session.method == Method.POST) return handlePost(uri, readBody(session))
        if (session.method != Method.GET) return error(404, "不明なエンドポイントです")

        when (uri) {
            "/", "/index.html", "/terminal.html" -> return asset(if (pty != null) "terminal.html" else "ide.html")
            "/ide.html", "/icon.svg", "/manifest.webmanifest" -> return asset(uri.removePrefix("/"))
        }

        val path = normalizePath(session.parameters["path"]?.firstOrNull())
        return when (uri) {
            "/api/info" -> json(200, buildJsonObject {
                put("ok", true)
                put("root", files.rootLabel)
                put("hostname", files.hostLabel)
                put("terminal", pty != null)
            })

            "/api/fs/list" -> {
                val entries = files.list(path).sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
                json(200, buildJsonObject {
                    put("ok", true)
                    put("path", path)
                    put("entries", buildJsonArray {
                        for (entry in entries) {
                            add(buildJsonObject {
                                put("name", entry.name)
                                put("path", entry.path)
                                put("type", if (entry.isDir) "dir" else "file")
                                put("size", entry.size)
                                put("mtime", entry.mtime)
                            })
                        }
                    })
                })
            }

            "/api/fs/read" -> {
                val data = files.read(path, MAX_TEXT_BYTES)
                if (isProbablyBinary(data)) throw BridgeException("バイナリファイルはエディタで開けません")
                json(200, buildJsonObject {
                    put("ok", true)
                    put("path", path)
                    put("content", String(data, Charsets.UTF_8))
                    put("size", data.size)
                })
            }

            "/api/fs/raw" -> {
                val data = files.read(path, MAX_RAW_BYTES)
                NanoHTTPD.newFixedLengthResponse(status(200), mimeTypeOf(path), ByteArrayInputStream(data), data.size.toLong())
                    .apply { addHeader("Cache-Control", "no-store") }
            }

            else -> error(404, "不明なエンドポイントです")
        }
    }

    private fun handlePost(uri: String, body: ByteArray): Response {
        val input = runCatching { Json.parseToJsonElement(String(body, Charsets.UTF_8)).jsonObject }.getOrNull()
            ?: throw BridgeException("JSON オブジェクトが必要です")
        val path = normalizePath(input.string("path"))
        return when (uri) {
            "/api/fs/write" -> {
                if (path.isEmpty()) throw BridgeException("ファイルの名前が必要です")
                val data = input.string("content").orEmpty().toByteArray(Charsets.UTF_8)
                files.write(path, data)
                json(200, buildJsonObject { put("ok", true); put("path", path); put("size", data.size) })
            }

            "/api/fs/mkdir" -> {
                if (path.isEmpty()) throw BridgeException("フォルダの名前が必要です")
                files.makeDir(path)
                json(200, buildJsonObject { put("ok", true); put("path", path); put("type", "dir") })
            }

            "/api/fs/delete" -> {
                if (path.isEmpty()) throw BridgeException("ルートは削除できません")
                files.delete(path, input["recursive"]?.jsonPrimitive?.booleanOrNull == true)
                json(200, buildJsonObject { put("ok", true); put("path", path); put("deleted", true) })
            }

            "/api/fs/rename" -> {
                val to = normalizePath(input.string("to"))
                if (path.isEmpty()) throw BridgeException("ルートは移動できません")
                if (to.isEmpty()) throw BridgeException("新しい名前が必要です")
                if (to != path) files.rename(path, to)
                json(200, buildJsonObject { put("ok", true); put("path", to) })
            }

            else -> error(404, "不明なエンドポイントです")
        }
    }

    // --------------------------------------------------------------- 応答

    private fun asset(name: String): Response {
        val stream = openAsset(name) ?: return error(404, "見つかりません")
        return NanoHTTPD.newChunkedResponse(status(200), mimeTypeOf(name), stream)
            .apply { addHeader("Cache-Control", "no-cache") }
    }

    private fun json(code: Int, body: JsonObject): Response =
        NanoHTTPD.newFixedLengthResponse(status(code), "application/json; charset=utf-8", body.toString())
            .apply { addHeader("Cache-Control", "no-store") }

    private fun error(code: Int, message: String): Response =
        json(code, buildJsonObject { put("ok", false); put("error", message) })

    private fun cookieHeader(): String = "$COOKIE_NAME=$token; Path=/; HttpOnly; SameSite=Strict"

    private fun matches(provided: String?): Boolean =
        !provided.isNullOrEmpty() && MessageDigest.isEqual(provided.toByteArray(), token.toByteArray())

    /**
     * 本文を UTF-8 として読む（NanoHTTPD の parseBody は charset の無い本文を US-ASCII で読み、日本語が化けるため使わない）。
     * 本文を読み残すと、同じ接続の次のリクエストが読めなくなるので、大きすぎるときも読み捨てる。
     */
    private fun readBody(session: IHTTPSession): ByteArray {
        val length = contentLength(session)
        if (length > MAX_BODY_BYTES) {
            drainBody(session)
            throw BridgeException("リクエストが大きすぎます", 413)
        }
        return ByteArray(length.toInt()).also { DataInputStream(session.inputStream).readFully(it) }
    }

    private fun drainBody(session: IHTTPSession) {
        var left = contentLength(session)
        val buffer = ByteArray(8192)
        while (left > 0) {
            val n = session.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) break
            left -= n
        }
    }

    private fun contentLength(session: IHTTPSession): Long =
        session.headers["content-length"]?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0

    // ------------------------------------------------------------ WebSocket

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = PtySocket(handshake, pty!!)

    /** ブラウザ（xterm.js）とシェルの間。入力は JSON（input・resize）、出力はバイナリのフレームで送る */
    private class PtySocket(handshake: IHTTPSession, private val backend: PtyBackend) : WebSocket(handshake) {
        private val params = handshake.parameters

        @Volatile
        private var session: PtySession? = null

        override fun onOpen() {
            val cols = params["cols"]?.firstOrNull()?.toIntOrNull()?.coerceIn(10, 1000) ?: 80
            val rows = params["rows"]?.firstOrNull()?.toIntOrNull()?.coerceIn(2, 1000) ?: 24
            val opened = try {
                backend.open(cols, rows)
            } catch (e: Exception) {
                val reason = (e.message ?: "シェルを開けません").take(100)
                runCatching { close(WebSocketFrame.CloseCode.InternalServerError, reason, false) }
                return
            }
            session = opened
            thread(name = "BridgePty", isDaemon = true) {
                val buffer = ByteArray(8192)
                try {
                    while (true) {
                        val n = opened.output.read(buffer)
                        if (n < 0) break
                        if (n > 0) send(buffer.copyOf(n))
                    }
                } catch (_: IOException) {
                    // 切断
                }
                runCatching { send("""{"type":"exit"}""") }
                runCatching { close(WebSocketFrame.CloseCode.NormalClosure, "exit", false) }
                cleanUp()
            }
        }

        override fun onMessage(message: WebSocketFrame) {
            val pty = session ?: return
            val obj = runCatching { Json.parseToJsonElement(message.textPayload).jsonObject }.getOrNull() ?: return
            runCatching {
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "input" -> pty.write(obj["data"]?.jsonPrimitive?.contentOrNull.orEmpty().toByteArray(Charsets.UTF_8))
                    "resize" -> pty.resize(
                        obj["cols"]?.jsonPrimitive?.intOrNull?.coerceIn(10, 1000) ?: return,
                        obj["rows"]?.jsonPrimitive?.intOrNull?.coerceIn(2, 1000) ?: return,
                    )
                }
            }
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) = cleanUp()

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException?) = cleanUp()

        private fun cleanUp() {
            session?.let { runCatching { it.close() } }
            session = null
        }
    }

    companion object {
        const val LOCALHOST = "127.0.0.1"
        const val COOKIE_NAME = "pocketdev_bridge"

        /** エディタで開ける大きさ（pocket-dev のサーバーと同じ） */
        const val MAX_TEXT_BYTES = 4L * 1024 * 1024

        /** PDF・画像の表示で読む大きさ */
        const val MAX_RAW_BYTES = 64L * 1024 * 1024

        const val MAX_BODY_BYTES = 8L * 1024 * 1024

        private const val SNIFF_BYTES = 8192

        /**
         * IDE から来たパスを、基準フォルダからの相対パス（"a/b"、基準フォルダは ""）に整える。
         * "." や ".." で基準フォルダの外に出ようとしたら断る。
         */
        fun normalizePath(raw: String?): String {
            val parts = raw.orEmpty().trim().split('/').filter { it.isNotEmpty() }
            if (parts.any { it == "." || it == ".." }) throw BridgeException("基準フォルダの外は参照できません", 403)
            return parts.joinToString("/")
        }

        /** NUL を含むか、先頭が UTF-8 として読めなければバイナリとみなす（agent/files.py と同じ判定） */
        fun isProbablyBinary(data: ByteArray): Boolean {
            val n = minOf(data.size, SNIFF_BYTES)
            for (i in 0 until n) if (data[i] == 0.toByte()) return true
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            // 末尾で切った多バイト文字は、続きがあるものとして扱う（endOfInput = false）
            return decoder.decode(ByteBuffer.wrap(data, 0, n), CharBuffer.allocate(n + 1), false).isError
        }

        /** Cookie ヘッダーから [name] の値を取り出す */
        fun cookieValue(header: String?, name: String): String? =
            header?.split(';')
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("$name=") }
                ?.substringAfter('=')

        fun mimeTypeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html; charset=utf-8"
            "svg" -> "image/svg+xml"
            "webmanifest" -> "application/manifest+json"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }

        private val REASONS = mapOf(
            200 to "OK", 400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden",
            404 to "Not Found", 409 to "Conflict", 413 to "Payload Too Large",
            500 to "Internal Server Error", 502 to "Bad Gateway", 503 to "Service Unavailable",
        )

        private fun status(code: Int): Response.IStatus = object : Response.IStatus {
            override fun getDescription(): String = "$code ${REASONS[code] ?: "Error"}"
            override fun getRequestStatus(): Int = code
        }
    }
}
