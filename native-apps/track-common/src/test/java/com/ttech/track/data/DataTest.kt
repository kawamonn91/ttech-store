package com.ttech.track.data

import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.TrackPoint
import com.ttech.track.map.TileBytesCache
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackFilesTest {
    private lateinit var dir: File
    private lateinit var files: TrackFiles

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("track-files").toFile()
        files = TrackFiles(File(dir, "drives"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun point(i: Int) = TrackPoint(1_000L * i, 35.0 + i * 1e-4, 139.0, speed = 10.0)

    @Test
    fun `追記した点を読み戻せる(閉じる前でも一定数ごとに書き出される)`() {
        val w = files.openWriter("100")
        for (i in 1..12) w.append(point(i))
        // 5点ごとに書き出しているので、閉じなくても10点までは読める(途中で止まっても残る)
        assertEquals(10, files.readTrack("100").size)
        w.close()
        val all = files.readTrack("100")
        assertEquals(12, all.size)
        assertEquals(1000L, all.first().timeMs)
        assertEquals(12_000L, all.last().timeMs)
    }

    @Test
    fun `同じIDで開き直すと続きに追記される(再開)`() {
        files.openWriter("7").use { it.append(point(1)) }
        files.openWriter("7").use { it.append(point(2)) }
        assertEquals(listOf(1000L, 2000L), files.readTrack("7").map { it.timeMs })
    }

    @Test
    fun `書き込み途中で切れた最後の行は読み飛ばし、それ以前は読める`() {
        files.openWriter("8").use { w -> for (i in 1..3) w.append(point(i)) }
        files.csvFile("8").appendText("4000,35.0004,139")
        assertEquals(3, files.readTrack("8").size)
    }

    @Test
    fun `概要は書いて読める。書き換えても壊れない`() {
        assertNull(files.readMeta("9"))
        files.writeMeta("9", """{"a":1}""")
        files.writeMeta("9", """{"a":2}""")
        assertEquals("""{"a":2}""", files.readMeta("9"))
        assertFalse(File(dir, "drives/9.json.tmp").exists())
    }

    @Test
    fun `IDの一覧は新しい順で、概要だけ・点だけの記録も含む`() {
        files.writeMeta("100", "{}")
        files.openWriter("300").use { it.append(point(1)) }
        files.writeMeta("200", "{}")
        files.openWriter("200").use { it.append(point(1)) }
        assertEquals(listOf("300", "200", "100"), files.ids())
    }

    @Test
    fun `削除すると3種類のファイルがすべて消える`() {
        files.writeMeta("5", "{}")
        files.openWriter("5").use { it.append(point(1)) }
        files.imageFile("5").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(files.hasImage("5"))
        files.delete("5")
        assertFalse(files.csvFile("5").exists())
        assertFalse(files.metaFile("5").exists())
        assertFalse(files.hasImage("5"))
        assertTrue(files.ids().isEmpty())
    }

    @Test
    fun `パスの区切りを含むIDは拒否する(別の場所を指せない)`() {
        assertThrows(IllegalArgumentException::class.java) { files.csvFile("../secret") }
        assertThrows(IllegalArgumentException::class.java) { files.metaFile("a/b") }
        assertThrows(IllegalArgumentException::class.java) { files.imageFile("") }
    }
}

class TileBytesCacheTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private var clock = System.currentTimeMillis() // 保存したファイルの更新時刻(実際の時刻)と同じ時計を基準にする

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        dir = Files.createTempDirectory("tiles").toFile()
    }

    @After
    fun tearDown() {
        server.close()
        dir.deleteRecursively()
    }

    private fun cache() = TileBytesCache(
        dir = dir,
        client = OkHttpClient(),
        userAgent = "TestApp/1.0 (test@example.com)",
        urlFor = { _, z, x, y -> server.url("/tiles/$z/$x/$y.png").toString() },
        maxAgeMs = 1000L,
        now = { clock },
    )

    @Test
    fun `取得したタイルを保存し、期限内は通信しない`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("PNGDATA").build())
        val c = cache()
        assertArrayEquals("PNGDATA".toByteArray(), c.bytes(MapStyle.Dark, 14, 100, 200))
        assertEquals(1, server.requestCount)
        assertArrayEquals("PNGDATA".toByteArray(), c.bytes(MapStyle.Dark, 14, 100, 200))
        assertEquals(1, server.requestCount) // 2回目は保存したものを使う
    }

    @Test
    fun `アプリを名乗るUser-Agentを付ける`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("x").build())
        cache().bytes(MapStyle.Light, 3, 1, 2)
        assertEquals("TestApp/1.0 (test@example.com)", server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun `期限が切れたら取り直す`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("OLD").build())
        server.enqueue(MockResponse.Builder().body("NEW").build())
        val c = cache()
        c.bytes(MapStyle.Dark, 5, 1, 1)
        clock += 5_000
        assertArrayEquals("NEW".toByteArray(), c.bytes(MapStyle.Dark, 5, 1, 1))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `取得できなくても、期限切れの保存分があればそれを使う`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("OLD").build())
        server.enqueue(MockResponse.Builder().code(503).build())
        val c = cache()
        c.bytes(MapStyle.Dark, 5, 1, 1)
        clock += 5_000
        assertArrayEquals("OLD".toByteArray(), c.bytes(MapStyle.Dark, 5, 1, 1))
    }

    @Test
    fun `保存が無く取得にも失敗したら null`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertNull(cache().bytes(MapStyle.Dark, 5, 9, 9))
    }

    @Test
    fun `通信できないとき(接続拒否)も例外にせず null`() = runBlocking {
        val c = cache()
        server.close()
        assertNull(c.bytes(MapStyle.Dark, 5, 2, 2))
    }

    @Test
    fun `空の応答は保存しない`() = runBlocking {
        server.enqueue(MockResponse.Builder().build())
        assertNull(cache().bytes(MapStyle.Dark, 5, 3, 3))
        assertFalse(cache().file(MapStyle.Dark, 5, 3, 3).exists())
    }
}
