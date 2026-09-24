package com.ttech.driverecord.domain

import com.ttech.track.domain.TrackPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 緯度1度あたりのメートル(地球の半径6,371,008.8mの場合) */
private const val M_PER_DEG = 111_195.08

/**
 * 1秒ごとの速度の列から、真北へ進む走行の記録を作る。
 * 位置は速度を積み上げて求めるので、正しい距離は速度の合計になる。
 */
private fun drive(
    speeds: List<Double>,
    startMs: Long = 1_790_000_000_000,
    bearing: (Int) -> Double? = { 0.0 },
    altitude: (Int) -> Double? = { null },
    hAcc: Double? = 5.0,
    jitterWhenStopped: Boolean = false,
): List<TrackPoint> {
    var lat = 35.0
    val out = ArrayList<TrackPoint>()
    for (i in speeds.indices) {
        if (i > 0) lat += speeds[i] * 1.0 / M_PER_DEG
        // 止まっているときは、GPSの位置が数mふらつく(記録の距離が増えないことの確認用)
        val wobble = if (jitterWhenStopped && speeds[i] < 0.5) (if (i % 2 == 0) 0.00002 else -0.00002) else 0.0
        out.add(
            TrackPoint(
                timeMs = startMs + i * 1000L,
                lat = lat, lon = 139.0 + wobble,
                altitude = altitude(i),
                speed = speeds[i],
                bearing = bearing(i),
                hAcc = hAcc,
            ),
        )
    }
    return out
}

private fun constant(speed: Double, seconds: Int) = List(seconds + 1) { speed }

class DriveStatsTest {
    @Test
    fun `一定速度の走行は、距離・時間・速度が正しい`() {
        val stats = DriveStatsCalculator.compute(drive(constant(10.0, 100)))
        assertEquals(1000.0, stats.distanceM, 10.0)
        assertEquals(100_000L, stats.durationMs)
        assertEquals(100_000L, stats.movingMs)
        assertEquals(10.0, stats.avgMovingSpeedMps, 0.2)
        assertEquals(10.0, stats.maxSpeedMps, 1e-9)
        assertEquals(101, stats.pointCount)
        assertTrue(stats.events.isEmpty())
        assertTrue(stats.stops.isEmpty())
        assertEquals(0, stats.gapCount)
        assertEquals(5.0, stats.avgAccuracyM!!, 1e-9)
    }

    @Test
    fun `途中の停止は、時間には入るが距離と動いた時間には入らない`() {
        val speeds = constant(10.0, 30) + constant(0.0, 40) + constant(10.0, 30)
        val stats = DriveStatsCalculator.compute(drive(speeds, jitterWhenStopped = true))
        // 30秒 + 30秒 走ったぶんの約600m。停止中のふらつきで距離が増えない
        assertEquals(600.0, stats.distanceM, 25.0)
        assertEquals(102_000L, stats.durationMs)
        assertTrue("動いた時間 ${stats.movingMs}", stats.movingMs in 58_000L..64_000L)
        assertEquals(1, stats.stops.size)
        assertTrue("停止 ${stats.stops[0].durationMs}ms", stats.stops[0].durationMs in 38_000L..42_000L)
    }

    @Test
    fun `出発前・到着後の停止は、信号待ちに数えない`() {
        val speeds = constant(0.0, 30) + constant(10.0, 30) + constant(0.0, 30)
        val stats = DriveStatsCalculator.compute(drive(speeds))
        assertTrue(stats.stops.isEmpty())
        assertEquals(300.0, stats.distanceM, 15.0)
    }

    @Test
    fun `20秒に満たない停止は数えない`() {
        val speeds = constant(10.0, 20) + constant(0.0, 10) + constant(10.0, 20)
        assertTrue(DriveStatsCalculator.compute(drive(speeds)).stops.isEmpty())
    }

    @Test
    fun `急ブレーキは1回にまとめて数え、加速度の大きさを持つ`() {
        val speeds = listOf(20.0, 20.0, 20.0, 15.0, 10.0, 5.0, 5.0, 5.0)
        val stats = DriveStatsCalculator.compute(drive(speeds))
        assertEquals(1, stats.hardBrakeCount)
        val e = stats.events.single { it.type == EventType.HardBrake }
        assertEquals(5.0, e.value, 0.6)
        assertTrue(e.speedMps >= 5.0)
    }

    @Test
    fun `ゆるやかな減速(毎秒2m)は急ブレーキではない`() {
        val speeds = (0..8).map { 20.0 - 2.0 * it }
        assertEquals(0, DriveStatsCalculator.compute(drive(speeds)).hardBrakeCount)
    }

    @Test
    fun `停止直前の小さな速度変化は急ブレーキにしない`() {
        val speeds = listOf(2.5, 2.0, 1.0, 0.0, 0.0)
        assertEquals(0, DriveStatsCalculator.compute(drive(speeds)).hardBrakeCount)
    }

    @Test
    fun `急加速を数える`() {
        val speeds = listOf(5.0, 5.0, 8.5, 12.0, 12.0, 12.0)
        val stats = DriveStatsCalculator.compute(drive(speeds))
        assertEquals(1, stats.hardAccelCount)
    }

    @Test
    fun `急ハンドル(横方向の加速度)は、速度と向きの変化の速さから求める`() {
        // 時速54km(15m/秒)で、1秒に20度ずつ曲がる → 15 × 0.349 = 約5.2 m/s²
        val speeds = constant(15.0, 8)
        val sharp = DriveStatsCalculator.compute(drive(speeds, bearing = { i -> (i * 20.0) % 360 }))
        assertTrue(sharp.sharpCornerCount >= 1)
        assertEquals(5.2, sharp.events.first { it.type == EventType.SharpCorner }.value, 0.3)

        // 1秒に5度(ゆるいカーブ)は該当しない
        val gentle = DriveStatsCalculator.compute(drive(speeds, bearing = { i -> i * 5.0 }))
        assertEquals(0, gentle.sharpCornerCount)
    }

    @Test
    fun `方位が0度をまたいでも急ハンドルにならない`() {
        val bearings = listOf(355.0, 359.0, 3.0, 7.0, 11.0, 15.0)
        val stats = DriveStatsCalculator.compute(drive(constant(15.0, 5), bearing = { bearings[it] }))
        assertEquals(0, stats.sharpCornerCount)
    }

    @Test
    fun `低速では急ハンドルを判定しない(駐車場での取り回し)`() {
        val stats = DriveStatsCalculator.compute(drive(constant(2.0, 8), bearing = { (it * 45.0) % 360 }))
        assertEquals(0, stats.sharpCornerCount)
    }

    @Test
    fun `1点だけ速度が跳ねても最高速度に影響しない`() {
        val speeds = MutableList(30) { 10.0 }
        speeds[15] = 60.0
        val stats = DriveStatsCalculator.compute(drive(speeds))
        assertEquals(10.0, stats.maxSpeedMps, 1e-9)
    }

    @Test
    fun `標高は、数メートルのふらつきを除いて上り・下りを数える`() {
        val speeds = constant(10.0, 120)
        val alt: (Int) -> Double = { i ->
            val base = when {
                i <= 60 -> i * 0.5 // 30m上る
                else -> 30.0 - (i - 60) * (20.0 / 60) // 20m下る
            }
            base + if (i % 2 == 0) 1.0 else -1.0 // ±1mのふらつき
        }
        val stats = DriveStatsCalculator.compute(drive(speeds, altitude = alt))
        assertEquals(30.0, stats.elevationGainM, 4.0)
        assertEquals(20.0, stats.elevationLossM, 4.0)
        assertTrue(stats.maxAltitudeM!! in 28.0..32.0)
        assertTrue(stats.minAltitudeM!! in 8.0..12.0 || stats.minAltitudeM!! < 2.0)
    }

    @Test
    fun `ふらつきだけの標高は、上り下りに数えない`() {
        val stats = DriveStatsCalculator.compute(drive(constant(10.0, 60), altitude = { 100.0 + if (it % 2 == 0) 1.5 else -1.5 }))
        assertEquals(0.0, stats.elevationGainM, 1e-9)
        assertEquals(0.0, stats.elevationLossM, 1e-9)
    }

    @Test
    fun `標高が取れていないときは null`() {
        val stats = DriveStatsCalculator.compute(drive(constant(10.0, 10)))
        assertNull(stats.minAltitudeM)
        assertEquals(0.0, stats.elevationGainM, 0.0)
    }

    @Test
    fun `GPSが途切れた区間は数え、途切れた間は急な操作を判定しない`() {
        val a = drive(constant(20.0, 10))
        // 30秒途切れて、速度が大きく変わって再開(トンネル)。途切れをまたぐ速度差は急ブレーキではない
        val b = drive(constant(5.0, 10), startMs = a.last().timeMs + 30_000).map { it.copy(lat = it.lat + 0.006) }
        val stats = DriveStatsCalculator.compute(a + b)
        assertEquals(1, stats.gapCount)
        assertEquals(0, stats.hardBrakeCount)
        // 走行200m + 途切れた間の直線 約467m + 再開後の50m。途切れた間の直線距離も入る
        assertEquals(717.0, stats.distanceM, 15.0)
    }

    @Test
    fun `速度が記録されていない点は、位置の差から求める`() {
        val pts = drive(constant(10.0, 30)).map { it.copy(speed = null) }
        val stats = DriveStatsCalculator.compute(pts)
        assertEquals(300.0, stats.distanceM, 5.0)
        assertEquals(10.0, stats.maxSpeedMps, 0.6)
        assertEquals(30_000L, stats.movingMs.coerceIn(29_000L, 30_000L))
    }

    @Test
    fun `速度帯ごとの時間の合計は、記録した時間と一致する`() {
        // 5km/h(1.39m/s) 10秒 → 40km/h(11.1) 20秒 → 90km/h(25) 30秒
        val speeds = constant(1.39, 10) + constant(11.1, 20) + constant(25.0, 30)
        val stats = DriveStatsCalculator.compute(drive(speeds))
        assertEquals(stats.durationMs, stats.speedBandMs.sum())
        assertTrue(stats.speedBandMs[0] >= 9_000L) // 〜10km/h
        assertTrue(stats.speedBandMs[4] >= 28_000L) // 80〜100km/h
        assertEquals(6, stats.speedBandMs.size)
    }

    @Test
    fun `速度帯の境界`() {
        assertEquals(0, DriveStatsCalculator.speedBand(9.9))
        assertEquals(1, DriveStatsCalculator.speedBand(10.0))
        assertEquals(3, DriveStatsCalculator.speedBand(79.9))
        assertEquals(5, DriveStatsCalculator.speedBand(100.0))
        assertEquals(5, DriveStatsCalculator.speedBand(180.0))
    }

    @Test
    fun `点が無い・1点だけでも壊れない`() {
        val empty = DriveStatsCalculator.compute(emptyList())
        assertEquals(0.0, empty.distanceM, 0.0)
        assertEquals(0, empty.pointCount)
        val one = DriveStatsCalculator.compute(drive(listOf(5.0)))
        assertEquals(0L, one.durationMs)
        assertEquals(0.0, one.distanceM, 0.0)
        assertEquals(1, one.pointCount)
    }

    @Test
    fun `平均速度は全体の距離÷時間、動いていた間の平均は停止を除く`() {
        val speeds = constant(10.0, 30) + constant(0.0, 30) + constant(10.0, 30)
        val stats = DriveStatsCalculator.compute(drive(speeds))
        assertEquals(stats.distanceM / (stats.durationMs / 1000.0), stats.avgSpeedMps, 1e-9)
        assertTrue(stats.avgMovingSpeedMps > stats.avgSpeedMps)
        assertEquals(stats.durationMs - stats.movingMs, stats.stoppedMs)
    }
}

class DriveSummaryTest {
    @Test
    fun `統計から概要を作ると、出発地・到着地・件数が入る`() {
        val points = drive(constant(10.0, 60))
        val stats = DriveStatsCalculator.compute(points)
        val s = DriveSummary.from("1790000000000", stats, points, Trigger.ANDROID_AUTO, finished = true)
        assertEquals(points.first().lat, s.startLat, 0.0)
        assertEquals(points.last().lat, s.endLat, 0.0)
        assertEquals(stats.distanceM, s.distanceM, 0.0)
        assertEquals(61, s.pointCount)
        assertEquals(Trigger.ANDROID_AUTO, s.trigger)
        assertTrue(s.finished)
    }

    @Test
    fun `概要はJSONに書いて読み戻せ、新しい項目が足されても古い記録を読める`() {
        val json = Json { ignoreUnknownKeys = true }
        val s = DriveSummary(id = "1", startTimeMs = 1L, distanceM = 1234.5, startLabel = "会津若松市")
        val text = json.encodeToString(s)
        assertEquals(s, json.decodeFromString<DriveSummary>(text))
        // 項目が足りない古い形式・知らない項目がある新しい形式のどちらも読める
        val old = json.decodeFromString<DriveSummary>("""{"id":"2","startTimeMs":5,"unknownFutureField":1}""")
        assertEquals("2", old.id)
        assertEquals(0.0, old.distanceM, 0.0)
    }
}
