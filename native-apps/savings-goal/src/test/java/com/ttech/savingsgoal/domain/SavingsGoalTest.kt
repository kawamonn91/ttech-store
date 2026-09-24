package com.ttech.savingsgoal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavingsGoalTest {
    @Test
    fun `利息なしなら残りを積立額で割った月数になる`() {
        // 300万 - 10万 = 290万 / 3万 = 96.67 → 97ヶ月目で到達
        assertEquals(97, SavingsGoal.monthsToReachGoal(3_000_000.0, 100_000.0, 30_000.0, 0.0))
    }

    @Test
    fun `ちょうど割り切れる場合はその月で到達する`() {
        assertEquals(10, SavingsGoal.monthsToReachGoal(100_000.0, 0.0, 10_000.0, 0.0))
    }

    @Test
    fun `利息があると到達が早まる`() {
        val withoutInterest = SavingsGoal.monthsToReachGoal(3_000_000.0, 100_000.0, 30_000.0, 0.0)!!
        val withInterest = SavingsGoal.monthsToReachGoal(3_000_000.0, 100_000.0, 30_000.0, 5.0)!!
        assertEquals(true, withInterest < withoutInterest)
    }

    @Test
    fun `年利12パーセントは月1パーセントの複利で増える`() {
        // 100万を月1%で運用、積立0: 1.01^n >= 1.1 となる最小のnは10
        assertEquals(10, SavingsGoal.monthsToReachGoal(1_100_000.0, 1_000_000.0, 0.0, 12.0))
    }

    @Test
    fun `すでに貯蓄が目標以上なら0ヶ月`() {
        assertEquals(0, SavingsGoal.monthsToReachGoal(100_000.0, 100_000.0, 0.0, 0.0))
    }

    @Test
    fun `50年以内に届かなければnull`() {
        assertNull(SavingsGoal.monthsToReachGoal(10_000_000.0, 0.0, 1_000.0, 0.0))
    }

    @Test
    fun `600ヶ月目ちょうどで届く場合は到達扱い`() {
        assertEquals(MAX_MONTHS, SavingsGoal.monthsToReachGoal(600_000.0, 0.0, 1_000.0, 0.0))
    }

    @Test
    fun `目標が0以下や積立額が負なら計算しない`() {
        assertNull(SavingsGoal.monthsToReachGoal(0.0, 0.0, 1_000.0, 0.0))
        assertNull(SavingsGoal.monthsToReachGoal(100_000.0, 0.0, -1.0, 0.0))
    }

    @Test
    fun `期間表記は端数の月があるときだけヶ月を付ける`() {
        assertEquals("8年1ヶ月", SavingsGoal.formatDuration(97))
        assertEquals("2年", SavingsGoal.formatDuration(24))
        assertEquals("0年5ヶ月", SavingsGoal.formatDuration(5))
    }
}
