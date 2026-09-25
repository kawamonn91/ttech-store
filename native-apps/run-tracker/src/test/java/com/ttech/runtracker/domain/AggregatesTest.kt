package com.ttech.runtracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatesTest {
    private val zone = Aggregates.JST

    private fun at(y: Int, m: Int, d: Int, h: Int = 7, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun run(id: String, startMs: Long, km: Double = 5.0, minutes: Int = 30, efforts: List<Effort> = emptyList()) = RunSummary(
        id = id, startTimeMs = startMs, distanceM = km * 1000, movingMs = minutes * 60_000L, elapsedMs = minutes * 60_000L,
        elevationGainM = 10.0, caloriesKcal = km * 60, efforts = efforts, finished = true,
    )

    @Test
    fun `週は月曜から始まる`() {
        assertEquals(DayOfWeek.MONDAY, LocalDate.of(2026, 9, 21).dayOfWeek)
        val monday = at(2026, 9, 21, 0, 0)
        assertEquals(monday, Aggregates.startOfWeek(at(2026, 9, 25, 15, 30)))
        assertEquals(monday, Aggregates.startOfWeek(at(2026, 9, 27, 23, 59)))
        assertEquals(monday, Aggregates.startOfWeek(monday))
        assertEquals(at(2026, 9, 28, 0, 0), Aggregates.startOfWeek(at(2026, 9, 28, 0, 1)))
    }

    @Test
    fun `合計を出す`() {
        val t = Aggregates.totals(listOf(run("1", at(2026, 9, 21), 5.0, 25), run("2", at(2026, 9, 22), 10.0, 55)))
        assertEquals(2, t.count)
        assertEquals(15_000.0, t.distanceM, 0.0)
        assertEquals(80 * 60_000L, t.movingMs)
        assertEquals(20.0, t.elevationGainM, 0.0)
        assertEquals(320.0, t.avgPaceSecPerKm, 0.01)
        assertEquals(0.0, Aggregates.totals(emptyList()).avgPaceSecPerKm, 0.0)
    }

    @Test
    fun `今週・今月・今年に絞る`() {
        val now = at(2026, 9, 25, 12)
        val runs = listOf(
            run("thisWeek", at(2026, 9, 21, 6)),
            run("lastWeek", at(2026, 9, 20, 22)),
            run("thisMonth", at(2026, 9, 2)),
            run("lastMonth", at(2026, 8, 31, 23)),
            run("lastYear", at(2025, 9, 25)),
        )
        assertEquals(listOf("thisWeek"), Aggregates.inWeek(runs, now).map { it.id })
        assertEquals(listOf("thisWeek", "lastWeek", "thisMonth"), Aggregates.inMonth(runs, now).map { it.id })
        assertEquals(listOf("thisWeek", "lastWeek", "thisMonth", "lastMonth"), Aggregates.inYear(runs, now).map { it.id })
    }

    @Test
    fun `今週の曜日ごとの距離`() {
        val now = at(2026, 9, 25)
        val days = Aggregates.daysOfWeek(
            listOf(run("a", at(2026, 9, 21), 5.0), run("b", at(2026, 9, 21, 18), 3.0), run("c", at(2026, 9, 24), 10.0), run("old", at(2026, 9, 20), 42.0)),
            now,
        )
        assertEquals(7, days.size)
        assertEquals(8000.0, days[0], 0.0)
        assertEquals(10_000.0, days[3], 0.0)
        assertEquals(0.0, days[6], 0.0)
    }

    @Test
    fun `週ごとの距離は、古い週から今週まで並び、走っていない週は0`() {
        val now = at(2026, 9, 25)
        val w = Aggregates.weekly(listOf(run("a", at(2026, 9, 22), 5.0), run("b", at(2026, 9, 8), 8.0), run("c", at(2026, 9, 10), 2.0)), now, weeks = 4)
        assertEquals(4, w.size)
        assertEquals(listOf(0.0, 10_000.0, 0.0, 5000.0), w.map { it.distanceM })
        assertEquals(listOf(0, 2, 0, 1), w.map { it.count })
        assertEquals(at(2026, 9, 21, 0, 0), w.last().startMs)
        assertEquals(at(2026, 8, 31, 0, 0), w.first().startMs)
    }

    @Test
    fun `距離ごとの自己ベストを選ぶ`() {
        val runs = listOf(
            run("a", at(2026, 9, 1), 5.0, efforts = listOf(Effort("1k", 330_000), Effort("5k", 1_800_000))),
            run("b", at(2026, 9, 8), 10.0, efforts = listOf(Effort("1k", 300_000), Effort("5k", 1_700_000), Effort("10k", 3_500_000))),
            run("c", at(2026, 9, 15), 5.0, efforts = listOf(Effort("1k", 300_000), Effort("5k", 1_750_000))),
        )
        val best = Aggregates.personalBests(runs).associateBy { it.def }
        assertEquals("b", best.getValue(EffortDef.K1).runId) // 同じ記録なら、先に出したほう
        assertEquals(1_700_000L, best.getValue(EffortDef.K5).timeMs)
        assertEquals(3_500_000L, best.getValue(EffortDef.K10).timeMs)
        assertNull(best[EffortDef.HALF])
        assertEquals(340.0, best.getValue(EffortDef.K5).paceSecPerKm, 0.01)
    }

    @Test
    fun `自己ベストを出したラン。最初のランは出さない`() {
        val a = run("a", at(2026, 9, 1), 5.0, efforts = listOf(Effort("1k", 330_000), Effort("5k", 1_800_000)))
        val b = run("b", at(2026, 9, 8), 5.0, efforts = listOf(Effort("1k", 320_000), Effort("5k", 1_850_000), Effort("10k", 3_900_000)))
        val all = listOf(a, b)
        assertTrue(Aggregates.recordsSetBy(a, all).isEmpty())
        assertEquals(setOf(EffortDef.K1, EffortDef.K10), Aggregates.recordsSetBy(b, all).toSet())
    }

    @Test
    fun `時間帯でタイトルをつける`() {
        assertEquals("朝ラン", RunTitle.auto(at(2026, 9, 25, 6)))
        assertEquals("昼ラン", RunTitle.auto(at(2026, 9, 25, 12)))
        assertEquals("夕方ラン", RunTitle.auto(at(2026, 9, 25, 17)))
        assertEquals("夜ラン", RunTitle.auto(at(2026, 9, 25, 21)))
        assertEquals("深夜ラン", RunTitle.auto(at(2026, 9, 25, 2)))
        assertEquals("朝ランです", RunSummary("1", 0, title = "朝ランです").displayTitle)
        assertEquals("深夜ラン", RunSummary("1", at(2026, 9, 25, 3), title = "  ").displayTitle)
    }
}

class BodyTest {
    @Test
    fun `BMI と判定`() {
        assertEquals(22.49, Body.bmi(65.0, 170.0)!!, 0.01)
        assertNull(Body.bmi(0.0, 170.0))
        assertNull(Body.bmi(65.0, 0.0))
        assertEquals("低体重(やせ)", Body.bmiCategory(17.9))
        assertEquals("普通体重", Body.bmiCategory(18.5))
        assertEquals("普通体重", Body.bmiCategory(24.9))
        assertEquals("肥満(1度)", Body.bmiCategory(25.0))
        assertEquals("肥満(2度)", Body.bmiCategory(31.0))
        assertEquals("肥満(4度)", Body.bmiCategory(41.0))
    }

    @Test
    fun `標準体重は BMI 22`() {
        assertEquals(63.58, Body.idealWeightKg(170.0)!!, 0.01)
        assertNull(Body.idealWeightKg(0.0))
    }

    @Test
    fun `その時点の体重は、それ以前でいちばん新しい記録`() {
        val e = listOf(WeightEntry(3000, 68.0), WeightEntry(1000, 70.0), WeightEntry(2000, 69.0))
        assertEquals(69.0, Body.weightAt(e, 2500)!!, 0.0)
        assertEquals(68.0, Body.weightAt(e, 9999)!!, 0.0)
        assertEquals(70.0, Body.weightAt(e, 1000)!!, 0.0)
        // それ以前の記録が無いときは、いちばん古い記録を使う
        assertEquals(70.0, Body.weightAt(e, 10)!!, 0.0)
        assertNull(Body.weightAt(emptyList(), 10))
    }
}
