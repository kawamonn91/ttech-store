package com.ttech.weightlog.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightLogicTest {
    private fun entry(date: String, kg: Double, id: String = date) = WeightEntry(id = id, date = date, weightKg = kg)

    @Test
    fun `BMIと判定`() {
        assertEquals(22.86, Body.bmi(70.0, 175.0)!!, 0.01)
        assertEquals("普通体重", Body.bmiCategory(22.0))
        assertEquals("低体重(やせ)", Body.bmiCategory(17.0))
        assertEquals("肥満(1度)", Body.bmiCategory(27.0))
        assertEquals("肥満(4度)", Body.bmiCategory(41.0))
        assertNull(Body.bmi(0.0, 170.0))
        assertNull(Body.bmi(60.0, 0.0))
    }

    @Test
    fun `標準体重はBMI22になる体重`() {
        assertEquals(67.375, Body.idealWeightKg(175.0)!!, 0.01)
        assertNull(Body.idealWeightKg(0.0))
    }

    @Test
    fun `同じ日付は上書きされる`() {
        val list = listOf(entry("2026-09-01", 70.0), entry("2026-09-02", 69.5))
        val next = upsertByDate(list, entry("2026-09-01", 69.0, id = "new"))
        assertEquals(2, next.size)
        assertEquals(69.0, next.first { it.date == "2026-09-01" }.weightKg, 0.0)
    }

    @Test
    fun `並び替え`() {
        val list = listOf(entry("2026-09-03", 68.0), entry("2026-09-01", 70.0), entry("2026-09-02", 69.0))
        assertEquals(listOf("2026-09-01", "2026-09-02", "2026-09-03"), sortedByDateAsc(list).map { it.date })
        assertEquals(listOf("2026-09-03", "2026-09-02", "2026-09-01"), sortedByDateDesc(list).map { it.date })
    }

    @Test
    fun `統計 開始からの増減・直近7日・目標までの進み具合`() {
        val today = LocalDate.of(2026, 9, 30)
        val entries = listOf(
            entry("2026-09-01", 70.0), // 開始
            entry("2026-09-24", 69.0),
            entry("2026-09-30", 68.0), // 最新
        )
        val profile = Profile(goalWeightKg = 65.0)
        val s = computeStats(entries, profile, today)
        assertEquals(68.0, s.latest!!.weightKg, 0.0)
        assertEquals(70.0, s.start!!.weightKg, 0.0)
        assertEquals(-2.0, s.changeFromStart!!, 0.001)
        assertEquals(-1.0, s.changeLast7Days!!, 0.001) // 9/24(69.0)→9/30(68.0)
        assertEquals(3.0, s.remainingToGoal!!, 0.001) // 68 - 65
        // (70-68)/(70-65)*100 = 40%
        assertEquals(40.0, s.progressPercent!!, 0.001)
        assertEquals(3, s.totalDays)
    }

    @Test
    fun `記録が1件だけなら、開始からの増減・直近7日はnull`() {
        val s = computeStats(listOf(entry("2026-09-01", 70.0)), Profile(), LocalDate.of(2026, 9, 1))
        assertNull(s.changeFromStart)
        assertNull(s.changeLast7Days)
    }

    @Test
    fun `目標体重が未設定なら、残りと進み具合はnull`() {
        val entries = listOf(entry("2026-09-01", 70.0), entry("2026-09-02", 69.0))
        val s = computeStats(entries, Profile(), LocalDate.of(2026, 9, 2))
        assertNull(s.remainingToGoal)
        assertNull(s.progressPercent)
    }

    @Test
    fun `連続記録日数`() {
        val today = LocalDate.of(2026, 9, 30)
        val consecutive = listOf("2026-09-28", "2026-09-29", "2026-09-30").map { entry(it, 70.0) }
        assertEquals(3, streakDays(consecutive, today))
        // 今日はまだ記録していないが、昨日まで続いていれば数える
        val untilYesterday = listOf("2026-09-27", "2026-09-28", "2026-09-29").map { entry(it, 70.0) }
        assertEquals(3, streakDays(untilYesterday, today))
        // 一昨日で途切れていれば0
        val gap = listOf("2026-09-25", "2026-09-26", "2026-09-27").map { entry(it, 70.0) }
        assertEquals(0, streakDays(gap, today))
        assertEquals(0, streakDays(emptyList(), today))
    }

    @Test
    fun `整形 範囲外の体重・日付・長すぎるメモを直す`() {
        val messy = WeightEntry(id = " x ", date = "でたらめ", weightKg = 999.0, bodyFatPercent = 200.0, memo = "m".repeat(1000))
        val c = messy.sanitized(nowMs = 5)
        assertEquals("x", c.id)
        assertEquals("2000-01-01", c.date)
        assertEquals(Limits.WEIGHT_RANGE.endInclusive, c.weightKg, 0.0)
        assertEquals(Limits.BODY_FAT_RANGE.endInclusive, c.bodyFatPercent!!, 0.0)
        assertEquals(Limits.MAX_MEMO, c.memo.length)
        assertEquals(5, c.updatedAtMs)
    }

    @Test
    fun `プロフィールの整形`() {
        val p = Profile(heightCm = 999.0, goalWeightKg = -5.0, goalDate = "でたらめ").sanitized()
        assertEquals(Limits.HEIGHT_RANGE.endInclusive, p.heightCm!!, 0.0)
        assertEquals(Limits.WEIGHT_RANGE.start, p.goalWeightKg!!, 0.0)
        assertNull(p.goalDate)
    }

    @Test
    fun `金額ならぬ体重の表示`() {
        assertEquals("68.0 kg", formatKg(68.0))
        assertEquals("-2.0 kg", formatKgSigned(-2.0))
        assertEquals("+1.5 kg", formatKgSigned(1.5))
        assertEquals("24.5%", formatPercent(24.5))
    }
}
