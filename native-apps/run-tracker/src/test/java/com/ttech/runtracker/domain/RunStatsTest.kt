package com.ttech.runtracker.domain

import com.ttech.track.domain.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val M_PER_DEG = 111_194.93

/** 北へ向かって走る記録を、1秒ごとの点で作る(試験用) */
class Gen(var t: Long = 1_790_000_000_000L, private val altitude: (Double) -> Double? = { null }) {
    var pos = 0.0
    val points = ArrayList<TrackPoint>()

    init {
        add(0.0)
    }

    fun add(speed: Double) {
        points.add(TrackPoint(t, 35.0 + pos / M_PER_DEG, 139.0, altitude = altitude(pos), speed = speed, hAcc = 5.0))
    }

    fun run(seconds: Int, mps: Double): Gen {
        repeat(seconds) {
            t += 1000
            pos += mps
            add(mps)
        }
        return this
    }

    fun stand(seconds: Int): Gen {
        repeat(seconds) {
            t += 1000
            add(0.0)
        }
        return this
    }

    /** 記録せずに時間だけ進める */
    fun skip(seconds: Int): Gen {
        t += seconds * 1000L
        return this
    }

    /** 記録せずに位置だけ動かす(一時停止の間に歩いた、など) */
    fun move(meters: Double): Gen {
        pos += meters
        return this
    }
}

class RunStatsTest {
    @Test
    fun `一定のペースで走ると、距離・時間・ペース・1kmごとの区間が合う`() {
        val s = RunStatsCalculator.compute(Gen().run(1000, 3.0).points)
        assertEquals(3000.0, s.distanceM, 5.0)
        assertEquals(1_000_000.0, s.movingMs.toDouble(), 2_000.0)
        assertEquals(1_000_000.0, s.elapsedMs.toDouble(), 1.0)
        assertEquals(333.3, s.avgPaceSecPerKm, 1.0)
        assertEquals(3, s.splits.size)
        s.splits.forEach {
            assertEquals(1000.0, it.distanceM, 0.5)
            assertEquals(333.3, it.paceSecPerKm, 1.5)
            assertFalse(it.isPartial)
        }
        assertEquals(listOf(1, 2, 3), s.splits.map { it.index })
    }

    @Test
    fun `1km未満の端数の区間も、最後に出る`() {
        val s = RunStatsCalculator.compute(Gen().run(833, 3.0).points) // 2499m
        assertEquals(3, s.splits.size)
        assertFalse(s.splits[1].isPartial)
        assertTrue(s.splits[2].isPartial)
        assertEquals(499.0, s.splits[2].distanceM, 3.0)
        assertEquals(333.3, s.splits[2].paceSecPerKm, 2.0)
    }

    @Test
    fun `端数が50m未満なら、区間に出さない`() {
        val s = RunStatsCalculator.compute(Gen().run(340, 3.0).points) // 1020m
        assertEquals(1, s.splits.size)
    }

    @Test
    fun `区間ごとに、ペースの違いが出る`() {
        val s = RunStatsCalculator.compute(Gen().run(334, 3.0).run(250, 4.0).points) // 1002m + 1000m
        assertEquals(2, s.splits.size)
        assertEquals(333.0, s.splits[0].paceSecPerKm, 3.0)
        assertEquals(250.0, s.splits[1].paceSecPerKm, 3.0)
        assertEquals(s.splits[1], s.fastestSplit)
    }

    @Test
    fun `一時停止をはさんだ区間は、動いた分も時間も数えない`() {
        val g = Gen().run(500, 3.0)
        val pauseStart = g.t
        g.skip(300).move(1000.0).run(500, 3.0)
        val pause = RunPause(pauseStart + 1_000, pauseStart + 299_000)

        val withPause = RunStatsCalculator.compute(g.points, listOf(pause))
        assertEquals(2997.0, withPause.distanceM, 6.0)
        assertEquals(998_000.0, withPause.movingMs.toDouble(), 3_000.0)
        assertEquals(0, withPause.gapCount)
        assertEquals(2, withPause.splits.count { !it.isPartial })

        // 一時停止を知らなければ、歩いた1kmも走った距離になり、GPSの途切れとして数える
        val without = RunStatsCalculator.compute(g.points)
        assertEquals(4000.0, without.distanceM, 6.0)
        assertEquals(1, without.gapCount)
    }

    @Test
    fun `止まっていた時間は、走っていた時間に入らない`() {
        val s = RunStatsCalculator.compute(Gen().run(300, 3.0).stand(60).run(300, 3.0).points)
        assertEquals(660_000.0, s.elapsedMs.toDouble(), 1.0)
        assertEquals(600_000.0, s.movingMs.toDouble(), 3_000.0)
        assertEquals(60_000.0, s.stoppedMs.toDouble(), 3_000.0)
        assertEquals(1800.0, s.distanceM, 5.0)
    }

    @Test
    fun `止まっている間の位置のぶれは、距離に足さない`() {
        val g = Gen().run(100, 3.0)
        // 立ち止まって、位置が2〜3mふらつく
        repeat(120) { i ->
            g.t += 1000
            g.points.add(TrackPoint(g.t, 35.0 + (g.pos + (if (i % 2 == 0) 2.0 else -1.0)) / M_PER_DEG, 139.0, speed = 0.1, hAcc = 6.0))
        }
        val s = RunStatsCalculator.compute(g.points)
        assertEquals(300.0, s.distanceM, 10.0)
    }

    @Test
    fun `GPSが途切れても、直線でつないで距離に数える`() {
        val g = Gen().run(100, 3.0)
        g.skip(29).move(90.0).run(100, 3.0) // 30秒ほどの途切れで、90m進んでいた
        val s = RunStatsCalculator.compute(g.points)
        assertEquals(1, s.gapCount)
        // 走った300m + 途切れの間の90m(と、その1歩ぶん) + 再開後の300m
        assertEquals(300.0 + 93.0 + 300.0 - 3.0, s.distanceM, 8.0)
    }

    @Test
    fun `自己ベストは、どこから走り始めてもいちばん速い区間を探す`() {
        val s = RunStatsCalculator.compute(Gen().run(800, 2.5).run(800, 4.0).points) // 2000m + 3200m
        val e = s.efforts.associate { it.def to it.timeMs }
        assertEquals(250_000.0, e.getValue(EffortDef.K1).toDouble(), 2_000.0)
        assertEquals(1609.344 / 4.0 * 1000, e.getValue(EffortDef.MILE).toDouble(), 3_000.0)
        assertEquals(750_000.0, e.getValue(EffortDef.K3).toDouble(), 3_000.0)
        assertEquals(1_520_000.0, e.getValue(EffortDef.K5).toDouble(), 5_000.0)
        assertNull(e[EffortDef.K10])
        assertNull(e[EffortDef.HALF])
    }

    @Test
    fun `走った距離に満たない距離の自己ベストは出さない`() {
        val s = RunStatsCalculator.compute(Gen().run(200, 3.0).points) // 600m
        assertTrue(s.efforts.isEmpty())
    }

    @Test
    fun `消費カロリーは、体重に比例し、速いほど大きい`() {
        val hour = Gen().run(3600, 10.0 / 3.6).points // 時速10kmで1時間
        val kg70 = RunStatsCalculator.compute(hour, weightKg = 70.0).caloriesKcal
        val kg35 = RunStatsCalculator.compute(hour, weightKg = 35.0).caloriesKcal
        assertEquals(734.0, kg70, 12.0)
        assertEquals(kg70 / 2, kg35, 1.0)

        val fast = RunStatsCalculator.compute(Gen().run(3600, 14.0 / 3.6).points, weightKg = 70.0).caloriesKcal
        assertTrue(fast > kg70)
        val walk = RunStatsCalculator.compute(Gen().run(3600, 5.0 / 3.6).points, weightKg = 70.0).caloriesKcal
        assertTrue(walk < kg70)
    }

    @Test
    fun `坂を登ると、区間ごとの標高の変化と、獲得標高が出る`() {
        val s = RunStatsCalculator.compute(Gen(altitude = { 100 + it * 0.05 }).run(1000, 3.0).points) // 100mごとに5m登る
        assertEquals(3, s.splits.size)
        s.splits.forEach { assertEquals(50.0, it.elevationDiffM!!, 3.0) }
        assertEquals(150.0, s.elevationGainM, 6.0)
        assertEquals(0.0, s.elevationLossM, 3.0)
        assertNotNull(s.series.altitudeM)
    }

    @Test
    fun `標高が無い記録でも、区間は出て、標高は空になる`() {
        val s = RunStatsCalculator.compute(Gen().run(400, 3.0).points)
        assertNull(s.splits.first().elevationDiffM)
        assertNull(s.series.altitudeM)
        assertNull(s.minAltitudeM)
    }

    @Test
    fun `グラフ用のデータは、点と同じ数で、ペースは速度からならしたもの`() {
        val g = Gen().run(300, 3.0)
        val s = RunStatsCalculator.compute(g.points)
        assertEquals(g.points.size, s.series.distKm.size)
        assertEquals(g.points.size, s.series.paceSecPerKm.size)
        assertEquals(333.3, s.series.paceSecPerKm[150], 1.0)
        assertEquals(0.9, s.series.distKm[300], 0.01)
    }

    @Test
    fun `止まっているところのペースは、走っているときのペースの少し外側までに収める`() {
        val s = RunStatsCalculator.compute(Gen().run(30, 3.0).stand(60).points)
        // 走っているときは 5:33/km。止まっている間も、グラフの縦軸を押し広げないよう、その1.4倍まで
        assertEquals(333.3 * 1.4, s.series.paceSecPerKm.last(), 3.0)
        assertTrue(s.series.paceSecPerKm.all { it <= 333.3 * 1.4 + 3.0 })
    }

    @Test
    fun `一度も走っていない(ずっと止まっている)記録は、ペースは上限のまま`() {
        val s = RunStatsCalculator.compute(Gen().stand(60).points)
        assertEquals(RunStatsCalculator.MAX_PACE, s.series.paceSecPerKm.last(), 0.0)
    }

    @Test
    fun `点が無い・1点だけでも壊れない`() {
        val empty = RunStatsCalculator.compute(emptyList())
        assertEquals(0.0, empty.distanceM, 0.0)
        assertTrue(empty.splits.isEmpty())
        val one = RunStatsCalculator.compute(Gen().points)
        assertEquals(0.0, one.distanceM, 0.0)
        assertEquals(0L, one.elapsedMs)
        assertTrue(one.splits.isEmpty())
    }

    @Test
    fun `MET は速さとともに増える(逆転しない)`() {
        var prev = 0.0
        var v = 0.0
        while (v <= 9.0) {
            val m = Calories.met(v)
            assertTrue("$v m/s で下がった: $prev → $m", m >= prev)
            prev = m
            v += 0.1
        }
        assertEquals(1.5, Calories.met(0.0), 0.0)
        assertEquals(23.0, Calories.met(20.0), 0.0)
    }

    @Test
    fun `bestTime は、窓が全体と同じ長さでも求まる`() {
        val d = doubleArrayOf(0.0, 100.0, 200.0, 300.0)
        val t = longArrayOf(0, 30_000, 60_000, 90_000)
        assertEquals(90_000L, RunStatsCalculator.bestTime(d, t, 300.0))
        assertEquals(30_000L, RunStatsCalculator.bestTime(d, t, 100.0))
        assertNull(RunStatsCalculator.bestTime(d, t, 301.0))
    }
}
