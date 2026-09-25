package com.ttech.runtracker.domain

import com.ttech.track.data.TrackFiles
import com.ttech.track.domain.TrackPoint
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RunRecorderTest {
    private lateinit var dir: File
    private lateinit var files: TrackFiles
    private var now = 1_790_000_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("run-recorder").toFile()
        files = TrackFiles(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun recorder() = RunRecorder(files, clock = { now })

    /** i 秒目(開始から)の点。位置は北へ [pos] m。3m/秒で走っている */
    private fun p(sec: Int, pos: Double, speed: Double = 3.0) =
        TrackPoint(1_790_000_000_000L + sec * 1000L, 35.0 + pos / 111_194.93, 139.0, speed = speed, hAcc = 5.0)

    /** 開始から [from] 秒目〜[to] 秒目(を含まない)まで、3m/秒で走る点を渡す。位置は秒数に応じて進む(一時停止中に動いた分は [offset] で足す) */
    private fun feed(r: RunRecorder, from: Int, to: Int, offset: Double = 0.0) {
        for (s in from until to) r.onPoint(p(s, s * 3.0 + offset))
    }

    @Test
    fun `一時停止をはさんでも、止まっていた間の移動は距離に入らず、一時停止の時間帯が残る`() {
        val r = recorder()
        r.start()
        feed(r, 0, 100)
        now += 100_000
        r.pause()
        // 一時停止中に届いた点は捨てる
        feed(r, 100, 400, offset = 1000.0)
        assertEquals(100, r.live()!!.points)
        now += 300_000
        r.resume()
        feed(r, 400, 500, offset = 1000.0)

        val s = r.finish(minDistanceM = 100, weightKg = 65.0)!!
        assertEquals(1, s.pauses.size)
        assertEquals(1_790_000_100_000L, s.pauses[0].startMs)
        assertEquals(1_790_000_400_000L, s.pauses[0].endMs)
        assertEquals(600.0, s.distanceM, 12.0) // 走った 100秒 + 100秒 の分だけ(3m/秒)
        assertEquals(200_000.0, s.movingMs.toDouble(), 4_000.0)
        assertEquals(65.0, s.weightKg!!, 0.0)
        assertTrue(s.finished)
        assertTrue(s.caloriesKcal > 0)
        // 保存もされている
        assertEquals(s.id, files.ids().single())
        assertTrue(files.readMeta(s.id)!!.contains("\"finished\":true"))
    }

    @Test
    fun `一時停止のまま終えても、そこまでの記録が残る`() {
        val r = recorder()
        r.start()
        feed(r, 0, 100)
        now += 100_000
        r.pause()
        now += 60_000
        val s = r.finish(minDistanceM = 100, weightKg = null)!!
        assertEquals(297.0, s.distanceM, 6.0)
        // 最後の点のあとの一時停止は、記録には関係ないので残さない
        assertTrue(s.pauses.isEmpty())
        assertNull(s.weightKg)
        assertFalse(r.isActive)
        assertFalse(r.isPaused)
    }

    @Test
    fun `短すぎるランは残さず、ファイルも消す`() {
        val r = recorder()
        r.start()
        feed(r, 0, 20) // 60m
        assertNull(r.finish(minDistanceM = 100, weightKg = null))
        assertTrue(files.ids().isEmpty())
        assertFalse(r.isActive)
    }

    @Test
    fun `点が無いランも残さない`() {
        val r = recorder()
        r.start()
        assertNull(r.finish(minDistanceM = 0, weightKg = null))
        assertTrue(files.ids().isEmpty())
        // 何も始めていなければ null
        assertNull(r.finish(minDistanceM = 0, weightKg = null))
    }

    @Test
    fun `走り出す前の停止と、終わったあとの停止は取り除く`() {
        val r = recorder()
        r.start()
        for (s in 0 until 30) r.onPoint(p(s, 0.0, speed = 0.0)) // 30秒、立ち止まって測位を待つ
        for (s in 30 until 330) r.onPoint(p(s, (s - 30) * 3.0))
        for (s in 330 until 420) r.onPoint(p(s, 299 * 3.0, speed = 0.0)) // 走り終わって90秒、立っている
        val summary = r.finish(minDistanceM = 100, weightKg = null)!!
        // 動いた300秒 + 前後の5秒ずつ
        assertEquals(310_000.0, summary.elapsedMs.toDouble(), 2_000.0)
        assertEquals(900.0, summary.distanceM, 10.0)
    }

    @Test
    fun `計測中の状況。走っている時間は一時停止を除き、ペースは速度から`() {
        val r = recorder()
        r.start()
        feed(r, 0, 60)
        val live = r.live()!!
        assertEquals(3.0, live.speedMps, 0.01)
        assertEquals(333.3, live.paceSecPerKm!!, 1.0)
        assertEquals(60, live.points)
        assertFalse(live.paused)

        now += 60_000
        r.pause()
        val paused = r.live()!!
        assertTrue(paused.paused)
        // 一時停止中は、走っている時間が進まない
        assertEquals(paused.activeMs(now), paused.activeMs(now + 120_000))
        assertEquals(60_000L, paused.activeMs(now + 120_000))

        now += 120_000
        r.resume()
        val resumed = r.live()!!
        assertEquals(120_000L, resumed.pausedClosedMs)
        assertEquals(60_000L, resumed.activeMs(now))
        assertEquals(90_000L, resumed.activeMs(now + 30_000))
    }

    @Test
    fun `ほぼ止まっているときは、ペースを出さない`() {
        val live = LiveRun("1", 0, 10, 0, 100.0, speedMps = 0.3, accuracyM = 5.0, lastPoint = null, paused = false, pausedClosedMs = 0, pauseStartMs = null, updatedAtMs = 0)
        assertNull(live.paceSecPerKm)
        assertEquals(600.0, live.avgPaceSecPerKm(60_000)!!, 0.01)
        assertNull(live.copy(distanceM = 5.0).avgPaceSecPerKm(60_000))
    }

    @Test
    fun `ランナーには速すぎる点(測位の誤り)は除く`() {
        val r = recorder()
        r.start()
        feed(r, 0, 100)
        // 1秒で100m(時速360km)動いた点は、誤りとして捨てる
        assertFalse(r.onPoint(p(100, 99 * 3.0 + 100.0)))
        assertTrue(r.onPoint(p(101, 101 * 3.0)))
    }

    @Test
    fun `アプリが止まっても、点と一時停止の時間帯から復旧できる`() {
        val r = recorder()
        r.start()
        feed(r, 0, 100)
        now += 100_000
        r.pause()
        now += 300_000
        r.resume()
        feed(r, 400, 500, offset = 1000.0)
        // ここでアプリが強制終了した(finish しない)。別のプロセスが起動して復旧する
        val revived = TrackFiles(dir)
        val recovered = RunRecorder.recover(revived, minDistanceM = 100, weightAt = { 60.0 })
        assertEquals(1, recovered.size)
        val s = recovered.single()
        assertTrue(s.finished)
        assertEquals(1, s.pauses.size)
        assertEquals(600.0, s.distanceM, 12.0)
        assertEquals(60.0, s.weightKg!!, 0.0)
        // 二度目は、もう終わっているので何もしない
        assertTrue(RunRecorder.recover(revived, minDistanceM = 100, weightAt = { 60.0 }).isEmpty())
    }

    @Test
    fun `復旧で、短すぎる記録は捨て、いま記録中のものは触らない`() {
        files.openWriter("111").use { it.append(p(0, 0.0)) }
        files.writeMeta("222", RunJson.encodeToString(RunSummary.serializer(), RunSummary(id = "222", startTimeMs = 222, finished = false)))
        files.openWriter("222").use { w -> for (s in 0 until 100) w.append(p(s, s * 3.0)) }
        assertTrue(RunRecorder.recover(files, minDistanceM = 100, weightAt = { null }, activeId = "222").isEmpty())
        assertNotNull(files.readMeta("222"))
        assertEquals(1, RunRecorder.recover(files, minDistanceM = 100, weightAt = { null }).size)
        assertEquals(listOf("222"), files.ids())
    }

    @Test
    fun `復旧しても、つけていたタイトルは残る`() {
        files.writeMeta("333", RunJson.encodeToString(RunSummary.serializer(), RunSummary(id = "333", startTimeMs = 333, title = "河川敷ラン", finished = false)))
        files.openWriter("333").use { w -> for (s in 0 until 100) w.append(p(s, s * 3.0)) }
        val s = RunRecorder.recover(files, minDistanceM = 100, weightAt = { null }).single()
        assertEquals("河川敷ラン", s.title)
    }
}
