package com.ttech.driverecord.domain

import com.ttech.track.data.TrackFiles
import com.ttech.track.domain.TrackPoint
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveControllerTest {
    private val start = DriveCommand.Start(Trigger.ANDROID_AUTO)

    @Test
    fun `Android Autoにつながったら記録を始める`() {
        val c = DriveController()
        assertEquals(start, c.onCarConnection(true, 0))
        assertTrue(c.isRecording)
    }

    @Test
    fun `自動記録がオフなら、つながっても始めない`() {
        val c = DriveController(DriveSettings(autoRecord = false))
        assertNull(c.onCarConnection(true, 0))
        assertFalse(c.isRecording)
    }

    @Test
    fun `切れても、待機時間が過ぎるまでは終えない`() {
        val c = DriveController(DriveSettings(disconnectGraceSec = 60))
        c.onCarConnection(true, 0)
        assertNull(c.onCarConnection(false, 10_000))
        assertEquals(70_000L, c.pendingStopAtMs)
        assertNull(c.onTick(69_999))
        assertTrue(c.isRecording)
        assertEquals(DriveCommand.Stop, c.onTick(70_000))
        assertFalse(c.isRecording)
        assertNull(c.pendingStopAtMs)
    }

    @Test
    fun `待機中に再接続したら、終えずに同じ記録を続ける`() {
        val c = DriveController(DriveSettings(disconnectGraceSec = 60))
        c.onCarConnection(true, 0)
        c.onCarConnection(false, 10_000)
        assertNull(c.onCarConnection(true, 30_000)) // 新しい記録は始めない(続行)
        assertNull(c.pendingStopAtMs)
        assertNull(c.onTick(200_000))
        assertTrue(c.isRecording)
    }

    @Test
    fun `手動で始めた記録は、Android Autoの接続とは関係なく続く`() {
        val c = DriveController()
        assertEquals(DriveCommand.Start(Trigger.MANUAL), c.manualStart())
        c.onCarConnection(true, 0)
        c.onCarConnection(false, 1000)
        assertNull(c.pendingStopAtMs)
        assertNull(c.onTick(10_000_000))
        assertTrue(c.isRecording)
        assertEquals(DriveCommand.Stop, c.manualStop())
    }

    @Test
    fun `すでに記録中の手動開始・記録していないときの手動停止は何もしない`() {
        val c = DriveController()
        assertNull(c.manualStop())
        c.manualStart()
        assertNull(c.manualStart())
    }

    @Test
    fun `自動で始めた記録を手動で止めたら、接続し直すまで始めない`() {
        val c = DriveController()
        c.onCarConnection(true, 0)
        assertEquals(DriveCommand.Stop, c.manualStop())
        assertFalse(c.isRecording)
        // 接続したままでも、設定を変えても勝手に再開しない
        assertNull(c.updateSettings(DriveSettings(disconnectGraceSec = 30), 5000))
        assertFalse(c.isRecording)
        // 一度切れて、また接続したら始める
        c.onCarConnection(false, 10_000)
        assertEquals(start, c.onCarConnection(true, 20_000))
    }

    @Test
    fun `自動記録をオフにしたら、自動で始めた記録はすぐ終える`() {
        val c = DriveController()
        c.onCarConnection(true, 0)
        assertEquals(DriveCommand.Stop, c.updateSettings(DriveSettings(autoRecord = false), 1000))
        assertFalse(c.isRecording)
    }

    @Test
    fun `自動記録をオフにしても、手動の記録は止めない`() {
        val c = DriveController()
        c.manualStart()
        assertNull(c.updateSettings(DriveSettings(autoRecord = false), 1000))
        assertTrue(c.isRecording)
    }

    @Test
    fun `接続したまま自動記録をオンにしたら、その場で始める`() {
        val c = DriveController(DriveSettings(autoRecord = false))
        c.onCarConnection(true, 0)
        assertEquals(start, c.updateSettings(DriveSettings(autoRecord = true), 1000))
    }

    @Test
    fun `同じ接続状態の通知が続いても、何度も始めない`() {
        val c = DriveController()
        assertEquals(start, c.onCarConnection(true, 0))
        assertNull(c.onCarConnection(true, 1000))
        assertNull(c.onCarConnection(true, 2000))
    }

    @Test
    fun `待機時間の設定を変えたら、次に切れたときから効く`() {
        val c = DriveController(DriveSettings(disconnectGraceSec = 60))
        c.onCarConnection(true, 0)
        c.updateSettings(DriveSettings(disconnectGraceSec = 300), 1000)
        c.onCarConnection(false, 2000)
        assertEquals(302_000L, c.pendingStopAtMs)
    }

    @Test
    fun `復旧した記録を続けるときは、その状態に合わせる`() {
        val c = DriveController()
        c.restore(Trigger.ANDROID_AUTO)
        assertTrue(c.isRecording)
        assertNull(c.onCarConnection(true, 0)) // 記録中なので、もう始めない
    }
}

class DriveRecorderTest {
    private lateinit var dir: File
    private lateinit var files: TrackFiles
    private var now = 1_790_000_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("recorder").toFile()
        files = TrackFiles(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun recorder() = DriveRecorder(files, clock = { now })

    /** 10m/秒で北へ走る点 */
    private fun p(i: Int, speed: Double = 10.0) =
        TrackPoint(now + i * 1000L, 35.0 + i * 10.0 / 111_195.08, 139.0, altitude = 20.0, speed = speed, bearing = 0.0, hAcc = 5.0)

    @Test
    fun `記録を始めると、概要(途中)とIDができ、点が追記される`() {
        val r = recorder()
        val id = r.start(Trigger.ANDROID_AUTO)
        assertEquals(now.toString(), id)
        assertTrue(r.isActive)
        val meta = DriveJson.decodeFromString<DriveSummary>(files.readMeta(id)!!)
        assertFalse(meta.finished)
        assertEquals(Trigger.ANDROID_AUTO, meta.trigger)
        for (i in 0..9) assertTrue(r.onPoint(p(i)))
        assertEquals(10, r.live()!!.points)
    }

    @Test
    fun `すでに記録中に始めても、同じIDのまま(二重に記録しない)`() {
        val r = recorder()
        val a = r.start(Trigger.MANUAL)
        assertEquals(a, r.start(Trigger.ANDROID_AUTO))
    }

    @Test
    fun `記録を終えると、統計つきの概要が保存される`() {
        val r = recorder()
        val id = r.start(Trigger.MANUAL)
        for (i in 0..60) r.onPoint(p(i))
        val s = r.finish(minDistanceM = 300)!!
        assertEquals(id, s.id)
        assertTrue(s.finished)
        assertEquals(600.0, s.distanceM, 10.0)
        assertEquals(61, s.pointCount)
        assertEquals(10.0, s.maxSpeedMps, 1e-9)
        assertFalse(r.isActive)
        val saved = DriveJson.decodeFromString<DriveSummary>(files.readMeta(id)!!)
        assertEquals(s, saved)
        assertEquals(61, files.readTrack(id).size)
    }

    @Test
    fun `短すぎるドライブは記録に残さず、ファイルも消す`() {
        val r = recorder()
        val id = r.start(Trigger.ANDROID_AUTO)
        for (i in 0..5) r.onPoint(p(i)) // 約50m
        assertNull(r.finish(minDistanceM = 300))
        assertNull(files.readMeta(id))
        assertTrue(files.readTrack(id).isEmpty())
        assertTrue(files.ids().isEmpty())
    }

    @Test
    fun `最短距離0なら、短いドライブも残す`() {
        val r = recorder()
        r.start(Trigger.MANUAL)
        for (i in 0..5) r.onPoint(p(i))
        assertNotNull(r.finish(minDistanceM = 0))
    }

    @Test
    fun `点が1つも無ければ残さない`() {
        val r = recorder()
        r.start(Trigger.MANUAL)
        assertNull(r.finish(0))
    }

    @Test
    fun `おかしい測位(精度が悪い・位置が飛んだ)は記録せず、数だけ数える`() {
        val r = recorder()
        r.start(Trigger.MANUAL)
        assertTrue(r.onPoint(p(0)))
        assertFalse(r.onPoint(p(1).copy(hAcc = 80.0)))
        assertFalse(r.onPoint(p(2).copy(lat = 36.5))) // 数百kmの飛び
        assertTrue(r.onPoint(p(3)))
        val live = r.live()!!
        assertEquals(2, live.points)
        assertEquals(2, live.rejected)
        // 記録したのは受け入れた2点だけ(除いた点はファイルに書かれない)
        r.finish(minDistanceM = 0)
        assertEquals(2, files.readTrack(now.toString()).size)
    }

    @Test
    fun `記録中でなければ点を受け取らない`() {
        assertFalse(recorder().onPoint(p(0)))
    }

    @Test
    fun `記録中の距離と最高速度は、点が来るたびに更新される`() {
        val r = recorder()
        r.start(Trigger.MANUAL)
        for (i in 0..30) r.onPoint(p(i, speed = if (i == 20) 25.0 else 10.0))
        val live = r.live()!!
        assertEquals(300.0, live.distanceM, 6.0)
        assertEquals(25.0, live.maxSpeedMps, 1e-9)
        assertEquals(10.0, live.speedMps, 1e-9)
        assertEquals(5.0, live.accuracyM!!, 1e-9)
    }

    @Test
    fun `止まっている間の位置のふらつきは、記録中の距離に足さない`() {
        val r = recorder()
        r.start(Trigger.MANUAL)
        val stopped = (0..20).map { TrackPoint(now + it * 1000L, 35.0, 139.0 + (if (it % 2 == 0) 0.00002 else -0.00002), speed = 0.1, hAcc = 5.0) }
        for (pt in stopped) r.onPoint(pt)
        assertEquals(0.0, r.live()!!.distanceM, 1e-9)
    }

    @Test
    fun `終了せずに止まった記録は、次の起動で点から復旧できる`() {
        val id = now.toString()
        // 記録の途中で止まった状態を作る: 概要は finished=false、点は書かれている
        val r = recorder()
        r.start(Trigger.ANDROID_AUTO)
        for (i in 0..60) r.onPoint(p(i))
        // finish() を呼ばずに放置(プロセスが終了した想定)
        val recovered = DriveRecorder.recover(files, minDistanceM = 300, activeId = null)
        assertEquals(1, recovered.size)
        assertEquals(id, recovered[0].id)
        assertTrue(recovered[0].finished)
        assertEquals(Trigger.ANDROID_AUTO, recovered[0].trigger)
        assertEquals(600.0, recovered[0].distanceM, 10.0)
    }

    @Test
    fun `復旧では、記録中のもの・終わっているものには触れない`() {
        val done = recorder()
        done.start(Trigger.MANUAL)
        for (i in 0..60) done.onPoint(p(i))
        val doneSummary = done.finish(300)!!

        now += 100_000
        val active = recorder()
        val activeId = active.start(Trigger.MANUAL)
        for (i in 0..60) active.onPoint(p(i))

        assertTrue(DriveRecorder.recover(files, 300, activeId = activeId).isEmpty())
        assertEquals(doneSummary, DriveJson.decodeFromString<DriveSummary>(files.readMeta(doneSummary.id)!!))
        assertFalse(DriveJson.decodeFromString<DriveSummary>(files.readMeta(activeId)!!).finished)
    }

    @Test
    fun `復旧で、短すぎる・点が空の記録は捨てる`() {
        files.writeMeta("111", DriveJson.encodeToString(DriveSummary(id = "111", startTimeMs = 111, finished = false)))
        files.openWriter("222").use { it.append(p(0)) }
        assertTrue(DriveRecorder.recover(files, 300).isEmpty())
        assertTrue(files.ids().isEmpty())
    }
}

class PlaceLabelTest {
    @Test
    fun `国名と郵便番号を省く`() {
        assertEquals("福島県会津若松市追手町2-41", PlaceLabel.shorten("日本、〒965-0000 福島県会津若松市追手町2-41"))
        assertEquals("東京都千代田区丸の内1丁目", PlaceLabel.shorten("日本、〒100-0005 東京都千代田区丸の内1丁目"))
        assertEquals("福島県会津若松市", PlaceLabel.shorten("福島県会津若松市"))
    }

    @Test
    fun `空なら null`() {
        assertNull(PlaceLabel.shorten(null))
        assertNull(PlaceLabel.shorten("  "))
        assertNull(PlaceLabel.shorten("日本、〒965-0000"))
    }
}
