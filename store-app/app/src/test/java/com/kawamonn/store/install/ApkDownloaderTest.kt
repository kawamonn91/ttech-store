package com.kawamonn.store.install

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ApkDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val downloader = ApkDownloader(OkHttpClient())
    private val payload = ByteArray(200_000) { (it % 251).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun bytes(b: ByteArray, code: Int = 200) =
        MockResponse.Builder().code(code).body(Buffer().write(b)).build()

    private fun target() = File(tmp.root, "apks/app.apk")

    @Test
    fun `最後までダウンロードし、進捗が単調に増えて最後は全体サイズになる`() = runBlocking {
        server.enqueue(bytes(payload))
        val progress = mutableListOf<Long>()
        val file = downloader.download(server.url("/a.apk").toString(), target()) { done, total ->
            progress += done
            assertEquals(payload.size.toLong(), total)
        }
        assertArrayEquals(payload, file.readBytes())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
        assertEquals(payload.size.toLong(), progress.last())
        assertFalse("完了後は .part が残らない", File(file.parentFile, "app.apk.part").exists())
    }

    @Test
    fun `途中まで落ちている場合は Range で続きから再開する`() = runBlocking {
        val target = target()
        target.parentFile!!.mkdirs()
        val half = payload.size / 2
        File(target.parentFile, "app.apk.part").writeBytes(payload.copyOfRange(0, half))
        server.enqueue(bytes(payload.copyOfRange(half, payload.size), code = 206))

        val file = downloader.download(server.url("/a.apk").toString(), target) { _, _ -> }

        assertEquals("bytes=$half-", server.takeRequest().headers["Range"])
        assertArrayEquals(payload, file.readBytes())
    }

    @Test
    fun `サーバーがRangeを無視して200を返したら最初からやり直す`() = runBlocking {
        val target = target()
        target.parentFile!!.mkdirs()
        File(target.parentFile, "app.apk.part").writeBytes(ByteArray(1000) { 9 })
        server.enqueue(bytes(payload)) // 200 (Range 非対応)

        val file = downloader.download(server.url("/a.apk").toString(), target) { _, _ -> }

        assertArrayEquals("古い部分ファイルを混ぜない", payload, file.readBytes())
    }

    @Test
    fun `HTTPエラーは例外にして、ファイルを作らない`() {
        server.enqueue(MockResponse.Builder().code(403).body("expired").build())
        val target = target()
        assertThrows(IOException::class.java) {
            runBlocking { downloader.download(server.url("/a.apk").toString(), target) { _, _ -> } }
        }
        assertFalse(target.exists())
    }

    @Test
    fun `完了済みの同名ファイルがあっても新しい内容で置き換える`() = runBlocking {
        val target = target()
        target.parentFile!!.mkdirs()
        target.writeBytes(ByteArray(10) { 1 })
        server.enqueue(bytes(payload))
        val file = downloader.download(server.url("/a.apk").toString(), target) { _, _ -> }
        assertArrayEquals(payload, file.readBytes())
        assertNull(server.takeRequest().headers["Range"])
    }
}
