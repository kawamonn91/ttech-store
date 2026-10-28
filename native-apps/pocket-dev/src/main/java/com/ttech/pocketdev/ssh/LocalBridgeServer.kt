package com.ttech.pocketdev.ssh

import android.content.res.AssetManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Android 内で動作し、WebView (Monaco / xterm) と SSH/SFTP を中継するローカルブリッジサーバー。
 */
class LocalBridgeServer(
    port: Int = 0,
    private val assets: AssetManager,
    private val sshClient: SshClient,
    private val token: String,
) : NanoWSD("127.0.0.1", port) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun serveHttp(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri
        val method = session.method
        val params = session.parameters

        // /health はトークン不要
        if (uri == "/health") {
            return jsonResponse(NanoHTTPD.Response.Status.OK, """{"ok":true,"service":"pocket-dev-ssh-bridge"}""")
        }

        // トークン検証 (URLパラメータ または Cookie)
        val tokenParam = params["token"]?.firstOrNull()
        val cookieHeader = session.headers["cookie"] ?: ""
        val hasValidCookie = cookieHeader.contains("token=$token")
        val isAuthorized = tokenParam == token || hasValidCookie

        if (!isAuthorized) {
            return jsonResponse(NanoHTTPD.Response.Status.UNAUTHORIZED, """{"ok":false,"error":"認証トークンが必要です"}""")
        }

        // 静的ファイル配信
        if (uri == "/" || uri == "/index.html" || uri == "/terminal.html") {
            return serveAsset("pocket-dev/web/terminal.html", "text/html; charset=utf-8")
        }
        if (uri == "/ide.html") {
            return serveAsset("pocket-dev/web/ide.html", "text/html; charset=utf-8")
        }
        if (uri == "/icon.svg") {
            return serveAsset("pocket-dev/web/icon.svg", "image/svg+xml")
        }
        if (uri == "/manifest.webmanifest") {
            return serveAsset("pocket-dev/web/manifest.webmanifest", "application/manifest+json")
        }

        // REST API
        if (method == NanoHTTPD.Method.GET) {
            return handleGetApi(uri, params)
        } else if (method == NanoHTTPD.Method.POST) {
            return handlePostApi(uri, session)
        }

        return jsonResponse(NanoHTTPD.Response.Status.NOT_FOUND, """{"ok":false,"error":"見つかりません"}""")
    }

    private fun handleGetApi(uri: String, params: Map<String, List<String>>): NanoHTTPD.Response {
        val relPath = params["path"]?.firstOrNull().orEmpty()
        return try {
            when (uri) {
                "/api/info" -> {
                    val body = buildJsonObject {
                        put("ok", true)
                        put("root", sshClient.resolveRemotePath(""))
                        put("hostname", "SSH Remote")
                    }.toString()
                    jsonResponse(NanoHTTPD.Response.Status.OK, body)
                }

                "/api/fs/list" -> {
                    val entries = sshClient.listDir(relPath)
                    val body = buildJsonObject {
                        put("ok", true)
                        put("path", relPath)
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
                    }.toString()
                    jsonResponse(NanoHTTPD.Response.Status.OK, body)
                }

                "/api/fs/read" -> {
                    val content = sshClient.readText(relPath)
                    val body = buildJsonObject {
                        put("ok", true)
                        put("path", relPath)
                        put("content", content)
                        put("size", content.length)
                    }.toString()
                    jsonResponse(NanoHTTPD.Response.Status.OK, body)
                }

                "/api/fs/raw" -> {
                    val bytes = sshClient.readBytes(relPath)
                    newChunkedResponse(NanoHTTPD.Response.Status.OK, "application/octet-stream", ByteArrayInputStream(bytes))
                }

                else -> jsonResponse(NanoHTTPD.Response.Status.NOT_FOUND, """{"ok":false,"error":"不明なエンドポイント"}""")
            }
        } catch (e: Exception) {
            jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, """{"ok":false,"error":"${escapeJson(e.message ?: "エラー")}"}""")
        }
    }

    private fun handlePostApi(uri: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val map = mutableMapOf<String, String>()
        try {
            session.parseBody(map)
        } catch (e: Exception) {
            // パース失敗時
        }
        val postData = map["postData"] ?: ""
        val jsonElement = runCatching { json.parseToJsonElement(postData).jsonObject }.getOrNull()

        return try {
            when (uri) {
                "/api/fs/write" -> {
                    val path = jsonElement?.get("path")?.jsonPrimitive?.contentOrNull ?: ""
                    val content = jsonElement?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
                    val writtenSize = sshClient.writeText(path, content)
                    val body = buildJsonObject {
                        put("ok", true)
                        put("path", path)
                        put("size", writtenSize)
                    }.toString()
                    jsonResponse(NanoHTTPD.Response.Status.OK, body)
                }

                "/api/fs/mkdir" -> {
                    val path = jsonElement?.get("path")?.jsonPrimitive?.contentOrNull ?: ""
                    sshClient.makeDir(path)
                    val body = buildJsonObject {
                        put("ok", true)
                        put("path", path)
                        put("type", "dir")
                    }.toString()
                    jsonResponse(NanoHTTPD.Response.Status.OK, body)
                }

                "/api/fs/delete" -> {
                    val path = jsonElement?.get("path")?.jsonPrimitive?.contentOrNull ?: ""
                    val recursive = jsonElement?.get("recursive")?.jsonPrimitive?.booleanOrNull ?: false
                    sshClient.delete(path, recursive)
                    val body = buildJsonObject {
                        put("ok", true)
                        put("path", path)
                        put("deleted", true)
                    }.toString()
                    jsonResponse(NanoHTTPD.Response.Status.OK, body)
                }

                "/api/fs/rename" -> {
                    val path = jsonElement?.get("path")?.jsonPrimitive?.contentOrNull ?: ""
                    val to = jsonElement?.get("to")?.jsonPrimitive?.contentOrNull ?: ""
                    sshClient.rename(path, to)
                    val body = buildJsonObject {
                        put("ok", true)
                        put("path", to)
                    }.toString()
                    jsonResponse(NanoHTTPD.Response.Status.OK, body)
                }

                else -> jsonResponse(NanoHTTPD.Response.Status.NOT_FOUND, """{"ok":false,"error":"不明なエンドポイント"}""")
            }
        } catch (e: Exception) {
            jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, """{"ok":false,"error":"${escapeJson(e.message ?: "エラー")}"}""")
        }
    }

    private fun serveAsset(assetPath: String, mimeType: String): NanoHTTPD.Response {
        return try {
            val stream = assets.open(assetPath)
            newChunkedResponse(NanoHTTPD.Response.Status.OK, mimeType, stream).apply {
                addHeader("Set-Cookie", "token=$token; Path=/; SameSite=Lax")
                addHeader("Cache-Control", "no-cache")
            }
        } catch (e: IOException) {
            jsonResponse(NanoHTTPD.Response.Status.NOT_FOUND, """{"ok":false,"error":"アセットが見つかりません: $assetPath"}""")
        }
    }

    private fun jsonResponse(status: NanoHTTPD.Response.Status, jsonText: String): NanoHTTPD.Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", jsonText).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    private fun escapeJson(str: String): String =
        str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): NanoWSD.WebSocket {
        return SshPtyWebSocket(handshake, sshClient)
    }

    /**
     * WebSocket 接続を SSH の ChannelShell (PTY) に接続する。
     */
    private class SshPtyWebSocket(
        private val handshakeRequest: NanoHTTPD.IHTTPSession,
        private val sshClient: SshClient,
    ) : NanoWSD.WebSocket(handshakeRequest) {

        private var shellChannel: com.jcraft.jsch.ChannelShell? = null
        private var shellIn: InputStream? = null
        private var shellOut: OutputStream? = null
        private var readerThread: Thread? = null

        override fun onOpen() {
            try {
                val params = handshakeRequest.parameters
                val cols = params["cols"]?.firstOrNull()?.toIntOrNull() ?: 80
                val rows = params["rows"]?.firstOrNull()?.toIntOrNull() ?: 24

                val shell = sshClient.openShell(cols, rows)
                val input = shell.inputStream
                val output = shell.outputStream

                shell.connect(10000)

                shellChannel = shell
                shellIn = input
                shellOut = output

                // SSH からの出力をクライアントに送る
                readerThread = thread(name = "SshPtyReader") {
                    val buffer = ByteArray(2048)
                    try {
                        while (shell.isConnected) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (read > 0) {
                                val text = String(buffer, 0, read, Charsets.UTF_8)
                                send(text)
                            }
                        }
                    } catch (e: Exception) {
                        // 切断時
                    }
                }
            } catch (e: Exception) {
                runCatching { close(NanoWSD.WebSocketFrame.CloseCode.InternalServerError, e.message ?: "SSH Error", false) }
            }
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            cleanUp()
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            val text = message.textPayload
            if (text.startsWith("{") && text.contains("\"resize\"")) {
                // リサイズ
                runCatching {
                    val obj = Json.parseToJsonElement(text).jsonObject
                    val cols = obj["cols"]?.jsonPrimitive?.intOrNull ?: 80
                    val rows = obj["rows"]?.jsonPrimitive?.intOrNull ?: 24
                    shellChannel?.setPtySize(cols, rows, cols * 8, rows * 16)
                }
                return
            }

            try {
                shellOut?.write(text.toByteArray(Charsets.UTF_8))
                shellOut?.flush()
            } catch (e: Exception) {
                // 送信失敗
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame?) {}

        override fun onException(exception: IOException?) {
            cleanUp()
        }

        private fun cleanUp() {
            runCatching { readerThread?.interrupt() }
            runCatching { shellIn?.close() }
            runCatching { shellOut?.close() }
            runCatching { shellChannel?.disconnect() }
            shellChannel = null
            shellIn = null
            shellOut = null
            readerThread = null
        }
    }
}
