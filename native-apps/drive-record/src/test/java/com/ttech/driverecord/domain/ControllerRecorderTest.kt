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
    fun `最初の点が遠く離れた古い位置だったら、続く点で基準を取り直す`() {
        val r = recorder()
        val id = r.start(Trigger.ANDROID_AUTO)
        // 測位の開始直後に、端末が直前に測っていた別の場所(カリフォルニア)の点が1つだけ入る
        assertTrue(r.onPoint(TrackPoint(now, 37.42, -122.08, speed = 0.0, hAcc = 5.0)))
        // そのあと、実際の走行の点が続く(日本)。1〜2点目は除かれ、3点目で基準が取り直される
        assertFalse(r.onPoint(p(1)))
        assertFalse(r.onPoint(p(2)))
        assertTrue(r.onPoint(p(3)))
        assertTrue(r.onPoint(p(4)))
        for (i in 5..60) r.onPoint(p(i))
        val s = r.finish(minDistanceM = 300)!!
        assertEquals(id, s.id)
        // 古い点は記録から消え、除いていた日本の点(1〜3点目)から数えて残る(60点、距離は約590m)
        assertEquals(35.0 + 10.0 / 111_195.08, s.startLat, 1e-6)
        assertEquals(60, s.pointCount)
        assertEquals(590.0, s.distanceM, 12.0)
        assertTrue(files.readTrack(id).none { it.lon < 0 })
    }

    @Test
    fun `整合しない飛び(バラバラの位置)では、基準を取り直さない`() {
        val r = recorder()
        r.start(Trigger.MANUAL)
        r.onPoint(p(0))
        // 互いに離れた場所のでたらめな点が続いても、最初の点のままにする
        assertFalse(r.onPoint(TrackPoint(now + 1000, 36.0, 138.0, hAcc = 5.0)))
        assertFalse(r.onPoint(TrackPoint(now + 2000, 34.0, 140.0, hAcc = 5.0)))
        assertFalse(r.onPoint(TrackPoint(now + 3000, 38.0, 137.0, hAcc = 5.0)))
        assertEquals(1, r.live()!!.points)
        assertEquals(35.0, r.live()!!.lastPoint!!.lat, 1e-9)
    }

    @Test
    fun `記録が長くなってからの飛びは、続いても基準を取り直さない`() {
        val r = recorder()
        r.start(Trigger.MANUAL)
        for (i in 0..80) r.onPoint(p(i))
        val before = r.live()!!.points
        // 走行の途中で、位置が遠くへ飛んだ点が続く(誤測位)
        val far = (81..86).map { TrackPoint(now + it * 1000L, 40.0 + it * 1e-4, 130.0, speed = 10.0, hAcc = 5.0) }
        far.forEach { assertFalse(r.onPoint(it)) }
        assertEquals(before, r.live()!!.points)
        // その後、正しい位置に戻れば、記録は続く
        assertTrue(r.onPoint(p(87)))
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

class IdleTrimTest {
    private fun pts(speeds: List<Double>): List<TrackPoint> =
        speeds.mapIndexed { i, v -> TrackPoint(1_000L * i, 35.0 + i * 1e-5, 139.0, speed = v, hAcc = 5.0) }

    @Test
    fun `出発前と到着後の停止を取り除き、前後5秒だけ残す`() {
        val speeds = List(60) { 0.0 } + List(100) { 12.0 } + List(90) { 0.0 }
        val trimmed = IdleTrim.trim(pts(speeds))
        // 走行100点 + 前5点 + 後5点
        assertEquals(110, trimmed.size)
        assertEquals(55_000L, trimmed.first().timeMs)
        assertEquals(164_000L, trimmed.last().timeMs)
    }

    @Test
    fun `途中の停止(信号待ち)は取り除かない`() {
        val speeds = List(30) { 0.0 } + List(20) { 12.0 } + List(40) { 0.0 } + List(20) { 12.0 } + List(30) { 0.0 }
        val trimmed = IdleTrim.trim(pts(speeds))
        assertEquals(20 + 40 + 20 + 10, trimmed.size)
    }

    @Test
    fun `最初から最後まで走っていれば、そのまま`() {
        val all = pts(List(50) { 12.0 })
        assertEquals(all, IdleTrim.trim(all))
    }

    @Test
    fun `一度も動いていない記録は、そのまま返す`() {
        val all = pts(List(50) { 0.1 })
        assertEquals(all, IdleTrim.trim(all))
    }

    @Test
    fun `点が少なければ触らない`() {
        assertEquals(1, IdleTrim.trim(pts(listOf(0.0))).size)
        assertTrue(IdleTrim.trim(emptyList()).isEmpty())
    }

    @Test
    fun `記録を終えると、到着後の停止が取り除かれて保存される`() {
        val dir = Files.createTempDirectory("trim").toFile()
        try {
            val files = TrackFiles(dir)
            var now = 1_790_000_000_000L
            val r = DriveRecorder(files, clock = { now })
            val id = r.start(Trigger.ANDROID_AUTO)
            // 走行60秒(約600m)のあと、止まったまま90秒(車を降りたが Android Auto はまだ切れていない)
            var lat = 35.0
            for (i in 0..59) {
                lat += 10.0 / 111_195.08
                r.onPoint(TrackPoint(now + i * 1000L, lat, 139.0, speed = 10.0, hAcc = 5.0))
            }
            for (i in 60..149) r.onPoint(TrackPoint(now + i * 1000L, lat, 139.0, speed = 0.0, hAcc = 5.0))
            val s = r.finish(minDistanceM = 300)!!
            assertEquals(65, s.pointCount) // 走行60点 + 到着後5点
            assertEquals(64_000L, s.durationMs)
            assertEquals(65, files.readTrack(id).size)
            assertTrue("平均 ${s.avgMovingSpeedMps}", s.avgMovingSpeedMps in 9.0..11.0)
        } finally {
            dir.deleteRecursively()
        }
    }
}
