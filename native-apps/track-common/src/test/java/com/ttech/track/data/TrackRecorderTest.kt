package com.ttech.track.data

import com.ttech.track.domain.TrackPoint
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackRecorderTest {
    private lateinit var dir: File
    private lateinit var files: TrackFiles
    private var now = 1_790_000_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("track-recorder").toFile()
        files = TrackFiles(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun recorder() = TrackRecorder(files, clock = { now })

    /** i 秒目の点。位置は起点から [posIdx] 歩(1歩=3m)北。既定では、3m/秒(時速約11km)で走り続ける */
    private fun p(i: Int, speed: Double = 3.0, posIdx: Int = i) =
        TrackPoint(now + i * 1000L, 35.0 + posIdx * 3.0 / 111_195.08, 139.0, speed = speed, hAcc = 5.0)

    @Test
    fun `一時停止の間は点を記録せず、再開すると記録が続く`() {
        val r = recorder()
        r.start()
        for (i in 0..9) assertTrue(r.onPoint(p(i)))
        r.pause()
        assertTrue(r.isPaused)
        for (i in 10..19) assertFalse(r.onPoint(p(i)))
        assertEquals(10, r.live()!!.points)
        assertTrue(r.live()!!.paused)
        r.resume()
        assertFalse(r.isPaused)
        assertTrue(r.onPoint(p(20)))
        assertEquals(11, r.live()!!.points)
    }

    @Test
    fun `一時停止中に動いた距離は、再開後の最初の点を「飛び」として除かない`() {
        val r = recorder()
        r.start()
        for (i in 0..9) r.onPoint(p(i))
        r.pause()
        // 止めている間に、けっこう動いた(5分で1.5km)
        now += 300_000
        r.resume()
        val after = TrackPoint(now, 35.0 + 1500.0 / 111_195.08, 139.0, speed = 3.0, hAcc = 5.0)
        assertTrue(r.onPoint(after))
    }

    @Test
    fun `記録中でなければ、一時停止・再開・点の受け取りは何もしない`() {
        val r = recorder()
        r.pause()
        r.resume()
        assertFalse(r.onPoint(p(0)))
        assertNull(r.live())
        assertNull(r.stop(idleMovingMps = null))
    }

    @Test
    fun `停止を除かない指定なら、記録した点がそのまま残る`() {
        val r = recorder()
        val id = r.start()
        for (i in 0..29) r.onPoint(p(i, speed = 0.0, posIdx = 0))
        for (i in 30..59) r.onPoint(p(i, posIdx = i - 30))
        val finished = r.stop(idleMovingMps = null)!!
        assertEquals(id, finished.id)
        assertEquals(60, finished.points.size)
        assertEquals(60, files.readTrack(id).size)
    }

    @Test
    fun `停止を除く指定なら、走り出す前の停止が取り除かれ、ファイルも書き直される`() {
        val r = recorder()
        val id = r.start()
        // 30秒間その場に立ち止まってから、60秒走る
        for (i in 0..29) r.onPoint(p(i, speed = 0.0, posIdx = 0))
        for (i in 30..89) r.onPoint(p(i, posIdx = i - 30))
        val finished = r.stop(idleMovingMps = 0.8)!!
        // 走行の60点 + 走り出す前の5秒
        assertEquals(65, finished.points.size)
        assertEquals(65, files.readTrack(id).size)
        assertEquals(now + 25_000L, finished.points.first().timeMs)
    }

    @Test
    fun `同じ記録に二重に始めても、IDは変わらない`() {
        val r = recorder()
        val a = r.start()
        assertEquals(a, r.start())
    }
}
