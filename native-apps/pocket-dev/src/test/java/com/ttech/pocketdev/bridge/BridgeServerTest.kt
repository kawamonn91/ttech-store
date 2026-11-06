package com.ttech.pocketdev.bridge

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class BridgeServerTest {

    private val token = "test-token"
    private val files = FakeFiles()
    private var server: BridgeServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    private fun start(pty: PtyBackend? = null): BridgeServer {
        val assets = mapOf("ide.html" to "<html>ide</html>", "terminal.html" to "<html>terminal</html>")
        return BridgeServer(token, files, pty, { name -> assets[name]?.let { ByteArrayInputStream(it.toByteArray()) } })
            .also { it.startServer(); server = it }
    }

    private val port get() = server!!.listeningPort

    // ------------------------------------------------------------- 認証

    @Test
    fun `healthはトークンなしで答える`() {
        start()
        assertEquals(200, get("/health", withToken = false).code)
    }

    @Test
    fun `トークンが無い・違うときは401`() {
        start()
        assertEquals(401, get("/api/info", withToken = false).code)
        assertEquals(401, get("/api/info?token=wrong", withToken = false).code)
    }

    @Test
    fun `URLのトークンで開くとCookieが発行され以後はCookieだけで通る`() {
        start()
        val first = get("/ide.html")
        assertEquals(200, first.code)
        val cookie = first.headers["Set-Cookie"].orEmpty()
        assertTrue(cookie, cookie.startsWith("${BridgeServer.COOKIE_NAME}=$token"))
        assertTrue(cookie.contains("HttpOnly"))

        val second = get("/api/info", withToken = false, cookie = cookie.substringBefore(';'))
        assertEquals(200, second.code)
    }

    @Test
    fun `トークンなしのWebSocketは断る`() {
        start(FakePty())
        val ws = WsClient(port, "/ws/pty")
        assertTrue(ws.statusLine, ws.statusLine.contains("401"))
        ws.close()
    }

    // --------------------------------------------------------- 機能の情報

    @Test
    fun `ターミナルが無い接続先はinfoのterminalがfalseで最初にIDEを出す`() {
        start(pty = null)
        val info = get("/api/info").json()
        assertFalse(info["terminal"]!!.jsonPrimitive.boolean)
        assertEquals("ルート", info["root"]!!.jsonPrimitive.content)
        assertEquals("<html>ide</html>", get("/").body)
        assertEquals("<html>ide</html>", get("/terminal.html").body)
    }

    @Test
    fun `ターミナルがある接続先はinfoのterminalがtrueで最初にターミナルを出す`() {
        start(FakePty())
        assertTrue(get("/api/info").json()["terminal"]!!.jsonPrimitive.boolean)
        assertEquals("<html>terminal</html>", get("/").body)
    }

    @Test
    fun `ターミナルが無い接続先へのWebSocketは404`() {
        start(pty = null)
        val ws = WsClient(port, "/ws/pty?token=$token")
        assertTrue(ws.statusLine, ws.statusLine.contains("404"))
        ws.close()
    }

    // ------------------------------------------------------------ ファイル

    @Test
    fun `charsetの無いJSONの日本語をUTF-8のまま保存して読み戻せる`() {
        start()
        val content = "# 日本語のコメント\nprint('こんにちは')\n"
        val write = post("/api/fs/write", """{"path":"src/メモ.py","content":${Json.encodeToString(String.serializer(), content)}}""")
        assertEquals(200, write.code)
        assertArrayEquals(content.toByteArray(Charsets.UTF_8), files.data["src/メモ.py"])

        val read = get("/api/fs/read?path=" + URLEncoder.encode("src/メモ.py", "UTF-8")).json()
        assertEquals(content, read["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `同じ接続で続けてPOSTしても本文を取り違えない`() {
        start()
        // HttpURLConnection は接続を使い回すので、本文の読み残しがあると2回目が壊れる
        repeat(3) { i ->
            assertEquals(200, post("/api/fs/write", """{"path":"f$i.txt","content":"$i"}""").code)
        }
        assertEquals("2", String(files.data["f2.txt"]!!))
    }

    @Test
    fun `一覧はフォルダが先で名前順`() {
        start()
        files.data["b.txt"] = byteArrayOf()
        files.data["A.txt"] = byteArrayOf()
        files.dirs += "z"
        val names = get("/api/fs/list").json()["entries"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(listOf("z", "A.txt", "b.txt"), names)
    }

    @Test
    fun `基準フォルダの外へのパスは403`() {
        start()
        assertEquals(403, get("/api/fs/read?path=../etc/passwd").code)
        assertEquals(403, post("/api/fs/write", """{"path":"a/../../x","content":""}""").code)
    }

    @Test
    fun `バイナリは開かずにエラーの文を返す`() {
        start()
        files.data["a.bin"] = byteArrayOf(1, 0, 2)
        val response = get("/api/fs/read?path=a.bin")
        assertEquals(400, response.code)
        assertEquals("バイナリファイルはエディタで開けません", response.json()["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `接続先のエラーはそのステータスと文で返す`() {
        start()
        val response = get("/api/fs/read?path=none.txt")
        assertEquals(404, response.code)
        assertEquals("ありません", response.json()["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `名前の変更と削除`() {
        start()
        files.data["a.txt"] = "x".toByteArray()
        assertEquals(200, post("/api/fs/rename", """{"path":"a.txt","to":"b.txt"}""").code)
        assertTrue(files.data.containsKey("b.txt"))
        assertEquals(200, post("/api/fs/delete", """{"path":"b.txt"}""").code)
        assertFalse(files.data.containsKey("b.txt"))
        assertEquals(400, post("/api/fs/delete", """{"path":""}""").code)
    }

    // ---------------------------------------------------------- ターミナル

    @Test
    fun `WebSocketの入力はJSONを解いてシェルに渡し出力はバイナリで返す`() {
        val pty = FakePty()
        start(pty)
        val ws = WsClient(port, "/ws/pty?token=$token&cols=100&rows=30")
        assertTrue(ws.statusLine, ws.statusLine.contains("101"))

        val session = pty.opened.poll(5, TimeUnit.SECONDS)
        assertNotNull(session)
        assertEquals(100 to 30, session!!.size)

        ws.sendText("""{"type":"input","data":"ls\n"}""")
        assertEquals("ls\n", String(session.written.poll(5, TimeUnit.SECONDS)!!))

        ws.sendText("""{"type":"resize","cols":120,"rows":40}""")
        assertEquals(120 to 40, session.resized.poll(5, TimeUnit.SECONDS))

        session.emit("こんにちは".toByteArray())
        val (opcode, payload) = ws.readFrame()
        assertEquals(2, opcode) // バイナリ
        assertEquals("こんにちは", String(payload))
        ws.close()
    }

    // ------------------------------------------------------------ 純関数

    @Test
    fun `パスを整える`() {
        assertEquals("", BridgeServer.normalizePath(null))
        assertEquals("", BridgeServer.normalizePath("/"))
        assertEquals("a/b", BridgeServer.normalizePath("/a//b/"))
        assertEquals("日本語/ファイル.txt", BridgeServer.normalizePath("日本語/ファイル.txt"))
    }

    @Test
    fun `バイナリの判定`() {
        assertFalse(BridgeServer.isProbablyBinary("print('あ')".toByteArray()))
        assertTrue(BridgeServer.isProbablyBinary(byteArrayOf(0x41, 0x00)))
        assertTrue(BridgeServer.isProbablyBinary(byteArrayOf(0xC3.toByte(), 0x28)))
        // 先頭 8192 バイトの境目で切れた多バイト文字はバイナリにしない
        val text = "a".repeat(8191) + "あ"
        assertFalse(BridgeServer.isProbablyBinary(text.toByteArray()))
    }

    @Test
    fun `Cookieの値を取り出す`() {
        assertEquals("abc", BridgeServer.cookieValue("x=1; pocketdev_bridge=abc; y=2", "pocketdev_bridge"))
        assertEquals(null, BridgeServer.cookieValue("pocketdev_bridge_old=abc", "pocketdev_bridge"))
        assertEquals(null, BridgeServer.cookieValue(null, "pocketdev_bridge"))
    }

    // ------------------------------------------------------------ 道具

    private data class HttpResult(val code: Int, val body: String, val headers: Map<String, String>) {
        fun json(): JsonObject = Json.parseToJsonElement(body).jsonObject
    }

    private fun get(path: String, withToken: Boolean = true, cookie: String? = null): HttpResult {
        val sep = if (path.contains('?')) "&" else "?"
        val url = "http://127.0.0.1:$port$path" + if (withToken) "${sep}token=$token" else ""
        return request(url, "GET", null, cookie)
    }

    private fun post(path: String, json: String): HttpResult =
        request("http://127.0.0.1:$port$path?token=$token", "POST", json.toByteArray(Charsets.UTF_8), null)

    private fun request(url: String, method: String, body: ByteArray?, cookie: String?): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        cookie?.let { connection.setRequestProperty("Cookie", it) }
        if (body != null) {
            connection.doOutput = true
            // IDE の fetch と同じく charset を付けない
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        val headers = connection.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") }
        return HttpResult(code, text, headers)
    }

    /** 中身を覚えておくだけの接続先 */
    private class FakeFiles : FileBackend {
        val data = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
        val dirs = mutableSetOf<String>()
        override val rootLabel = "ルート"
        override val hostLabel = "テスト"

        override fun list(path: String): List<FileEntry> =
            data.keys.map { FileEntry(it, it, false, 0, 0) } + dirs.map { FileEntry(it, it, true, 0, 0) }

        override fun read(path: String, maxBytes: Long): ByteArray = data[path] ?: throw BridgeException("ありません", 404)

        override fun write(path: String, data: ByteArray) {
            this.data[path] = data
        }

        override fun makeDir(path: String) {
            dirs += path
        }

        override fun delete(path: String, recursive: Boolean) {
            data.remove(path)
        }

        override fun rename(from: String, to: String) {
            data[to] = data.remove(from) ?: throw BridgeException("ありません", 404)
        }
    }

    private class FakePty : PtyBackend {
        val opened = LinkedBlockingQueue<FakeSession>()
        override fun open(cols: Int, rows: Int): PtySession = FakeSession(cols to rows).also { opened.add(it) }
    }

    private class FakeSession(val size: Pair<Int, Int>) : PtySession {
        private val pipeOut = PipedOutputStream()
        override val output: InputStream = PipedInputStream(pipeOut)
        val written = LinkedBlockingQueue<ByteArray>()
        val resized = LinkedBlockingQueue<Pair<Int, Int>>()

        fun emit(bytes: ByteArray) {
            pipeOut.write(bytes)
            pipeOut.flush()
        }

        override fun write(data: ByteArray) {
            written.add(data)
        }

        override fun resize(cols: Int, rows: Int) {
            resized.add(cols to rows)
        }

        override fun close() {
            runCatching { pipeOut.close() }
        }
    }

    /** テスト用の最小の WebSocket クライアント（クライアントからのフレームはマスクする） */
    private class WsClient(port: Int, path: String) {
        private val socket = Socket("127.0.0.1", port).apply { soTimeout = 5000 }
        private val input = DataInputStream(socket.getInputStream())
        val statusLine: String

        init {
            val key = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
            val request = "GET $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray())
            statusLine = readLine()
            while (readLine().isNotEmpty()) { /* ヘッダーを読み飛ばす */ }
        }

        private fun readLine(): String {
            val out = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0 || b == '\n'.code) break
                if (b != '\r'.code) out.append(b.toChar())
            }
            return out.toString()
        }

        fun sendText(text: String) {
            val payload = text.toByteArray()
            val mask = byteArrayOf(1, 2, 3, 4)
            val frame = java.io.ByteArrayOutputStream()
            frame.write(0x81)
            require(payload.size < 126)
            frame.write(0x80 or payload.size)
            frame.write(mask)
            payload.forEachIndexed { i, b -> frame.write(b.toInt() xor mask[i % 4].toInt()) }
            socket.getOutputStream().write(frame.toByteArray())
        }

        fun readFrame(): Pair<Int, ByteArray> {
            val b0 = input.readUnsignedByte()
            var length = (input.readUnsignedByte() and 0x7f).toLong()
            if (length == 126L) length = input.readUnsignedShort().toLong()
            if (length == 127L) length = input.readLong()
            val payload = ByteArray(length.toInt()).also { input.readFully(it) }
            return (b0 and 0x0f) to payload
        }

        fun close() = runCatching { socket.close() }
    }
}
