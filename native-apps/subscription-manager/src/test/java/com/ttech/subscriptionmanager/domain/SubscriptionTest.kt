package com.ttech.subscriptionmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SubscriptionTest {
    private fun s(id: String, amount: Long, cycle: Cycle, date: String = "2026-10-01") = Subscription(id, "s$id", amount, cycle, date)

    @Test
    fun `入力値から登録内容を作る`() {
        assertEquals(
            Subscription("1", "動画配信", 990, Cycle.MONTHLY, "2026-10-01"),
            buildSubscription("1", " 動画配信 ", "990", Cycle.MONTHLY, "2026-10-01"),
        )
    }

    @Test
    fun `サービス名が空や金額が0なら登録しない`() {
        assertNull(buildSubscription("1", " ", "990", Cycle.MONTHLY, "2026-10-01"))
        assertNull(buildSubscription("1", "a", "0", Cycle.MONTHLY, "2026-10-01"))
        assertNull(buildSubscription("1", "a", "", Cycle.MONTHLY, "2026-10-01"))
    }

    @Test
    fun `追加すると次回支払日の近い順に並ぶ`() {
        val list = listOf(s("1", 100, Cycle.MONTHLY, "2026-10-05"), s("2", 100, Cycle.MONTHLY, "2026-10-20"))
        val next = list.addSorted(s("3", 100, Cycle.MONTHLY, "2026-10-10"))
        assertEquals(listOf("1", "3", "2"), next.map { it.id })
    }

    @Test
    fun `月あたり合計は年払いを12で割って四捨五入する`() {
        // 990 + 5900/12(=491.67) = 1481.67 → 1482
        assertEquals(1482L, listOf(s("1", 990, Cycle.MONTHLY), s("2", 5900, Cycle.YEARLY)).monthlyTotal())
        assertEquals(0L, emptyList<Subscription>().monthlyTotal())
    }

    @Test
    fun `次回支払日までの日数と表示`() {
        val today = LocalDate.of(2026, 9, 24)
        assertEquals(7L, daysUntil("2026-10-01", today))
        assertEquals(0L, daysUntil("2026-09-24", today))
        assertEquals(-2L, daysUntil("2026-09-22", today))
        assertEquals("あと7日", remainingLabel(7))
        assertEquals("2日超過", remainingLabel(-2))
    }
}
