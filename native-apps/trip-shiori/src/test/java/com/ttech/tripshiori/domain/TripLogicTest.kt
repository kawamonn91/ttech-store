package com.ttech.tripshiori.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripLogicTest {
    private var seq = 0
    private fun newId() = "id${++seq}"

    private fun trip(
        start: String = "2026-10-10",
        end: String = "2026-10-12",
        items: List<ScheduleItem> = emptyList(),
        travelers: List<String> = emptyList(),
    ) = Trip(id = "t", title = "テスト", startDate = start, endDate = end, items = items, travelers = travelers)

    private fun item(id: String, day: Int, time: String = "", cost: Int = 0, kind: ItemKind = ItemKind.SIGHT) =
        ScheduleItem(id = id, day = day, time = time, title = id, kind = kind, cost = cost)

    @Test
    fun `日数と何泊何日かを数える`() {
        assertEquals(3, trip().dayCount())
        assertEquals("2泊3日", trip().durationLabel())
        assertEquals("日帰り", trip(end = "2026-10-10").durationLabel())
        assertEquals("1泊2日", trip(end = "2026-10-11").durationLabel())
    }

    @Test
    fun `終了が開始より前でも、1日として扱う。日数は上限で止める`() {
        assertEquals(1, trip(start = "2026-10-10", end = "2026-10-01").dayCount())
        assertEquals(Limits.MAX_DAYS, trip(start = "2026-01-01", end = "2027-12-31").dayCount())
    }

    @Test
    fun `日付の表示は曜日つき`() {
        // 2026-10-10 は土曜日
        assertEquals("2026/10/10(土)〜10/12(月)", trip().periodLabel())
        assertEquals("2026/10/10(土)", trip(end = "2026-10-10").periodLabel())
        assertEquals("1日目 10/10(土)", trip().dayLabel(0))
        assertEquals("3日目 10/12(月)", trip().dayLabel(2))
    }

    @Test
    fun `予定は時刻順。時刻が未定のものは後ろに、追加した順`() {
        val t = trip(
            items = listOf(
                item("b", 0, "13:00"), item("x", 0, ""), item("a", 0, "09:30"), item("y", 0, ""), item("c", 1, "08:00"),
            ),
        )
        assertEquals(listOf("a", "b", "x", "y"), t.itemsOn(0).map { it.id })
        assertEquals(listOf("c"), t.itemsOn(1).map { it.id })
        assertTrue(t.itemsOn(2).isEmpty())
    }

    @Test
    fun `日程を短くしても、範囲外になった予定は消さずに最終日へ寄せる`() {
        val t = trip(items = listOf(item("a", 0), item("b", 1), item("c", 2)))
        val shorter = t.withDates(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11))
        assertEquals(2, shorter.dayCount())
        assertEquals(listOf(0, 1, 1), shorter.items.map { it.day })
        // 開始日を動かしても、何日目かは変わらない
        val moved = t.withDates(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3))
        assertEquals(listOf(0, 1, 2), moved.items.map { it.day })
        assertEquals("2026-11-01", moved.startDate)
    }

    @Test
    fun `日程の終わりが開始より前なら、開始日1日にする`() {
        val t = trip().withDates(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1))
        assertEquals("2026-10-10", t.endDate)
    }

    @Test
    fun `今日との関係`() {
        val t = trip()
        assertEquals(TripPhase.Upcoming(3), t.phase(LocalDate.of(2026, 10, 7)))
        assertEquals("あと3日", t.phase(LocalDate.of(2026, 10, 7)).label())
        // 出発の日から「旅行中」
        assertEquals(TripPhase.Ongoing(0), t.phase(LocalDate.of(2026, 10, 10)))
        assertEquals("旅行中 1日目", t.phase(LocalDate.of(2026, 10, 10)).label())
        assertEquals(TripPhase.Upcoming(1), t.phase(LocalDate.of(2026, 10, 9)))
        assertEquals(TripPhase.Ongoing(2), t.phase(LocalDate.of(2026, 10, 12)))
        assertEquals("旅行中 2日目", t.phase(LocalDate.of(2026, 10, 11)).label())
        assertEquals(TripPhase.Finished, t.phase(LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun `費用の集計と1人あたり(端数は切り上げ)`() {
        val t = trip(
            items = listOf(item("a", 0, cost = 1000, kind = ItemKind.MEAL), item("b", 0, cost = 2500, kind = ItemKind.MOVE), item("c", 2, cost = 700, kind = ItemKind.MEAL), item("d", 1)),
            travelers = listOf("A", "B", "C"),
        )
        assertEquals(4200, t.totalCost())
        assertEquals(mapOf(ItemKind.MEAL to 1700, ItemKind.MOVE to 2500), t.costByKind())
        assertEquals(listOf(3500, 0, 700), t.costByDay())
        assertEquals(1400, t.costPerPerson())
        assertEquals(1401, t.copy(items = t.items + item("e", 1, cost = 1)).costPerPerson()) // 4201 / 3 → 切り上げ
        assertNull(t.copy(travelers = listOf("A")).costPerPerson())
    }

    @Test
    fun `金額の表示は3桁区切り`() {
        assertEquals("¥0", yen(0))
        assertEquals("¥1,234,567", yen(1_234_567))
    }

    @Test
    fun `持ち物の追加は、同じ名前(空白・大文字小文字を無視)を重ねない`() {
        val t = trip().addPacking(listOf("財布", " 財布 ", "Charger", "charger", "", "傘"), ::newId)
        assertEquals(listOf("財布", "Charger", "傘"), t.packing.map { it.name })
        val again = t.addPacking(listOf("傘", "帽子"), ::newId)
        assertEquals(listOf("財布", "Charger", "傘", "帽子"), again.packing.map { it.name })
    }

    @Test
    fun `持ち物のチェックと進み具合、全部外す`() {
        var t = trip().addPacking(listOf("A", "B", "C"), ::newId)
        t = t.togglePacking(t.packing[0].id).togglePacking(t.packing[2].id)
        assertEquals(2 to 3, t.packingProgress())
        assertEquals(0 to 3, t.resetPacking().packingProgress())
        t = t.removePacking(t.packing[1].id)
        assertEquals(listOf("A", "C"), t.packing.map { it.name })
    }

    @Test
    fun `予定の追加・更新・削除`() {
        var t = trip()
        t = t.upsertItem(item("a", 0, "10:00"))
        t = t.upsertItem(item("b", 0, "11:00"))
        t = t.upsertItem(item("a", 1, "12:00")) // 同じ id は置き換え
        assertEquals(listOf("a" to 1, "b" to 0), t.items.map { it.id to it.day })
        assertEquals(listOf("b"), t.removeItem("a").items.map { it.id })
    }

    @Test
    fun `時刻の入力をそろえる`() {
        assertEquals("09:05", normalizeTime("9:05"))
        assertEquals("09:05", normalizeTime("9:5"))
        assertEquals("09:05", normalizeTime("0905"))
        assertEquals("09:05", normalizeTime("905"))
        assertEquals("18:30", normalizeTime("18：30"))
        assertEquals("", normalizeTime("  "))
        assertNull(normalizeTime("25:00"))
        assertNull(normalizeTime("12:60"))
        assertNull(normalizeTime("あさ"))
        assertNull(normalizeTime("12"))
    }

    @Test
    fun `整形 日付・件数・文字数・範囲外の日・時刻を直す`() {
        val messy = Trip(
            id = "t", title = "  ", startDate = "でたらめ", endDate = "2026-10-01",
            items = listOf(
                ScheduleItem("a", day = 99, time = "25:99", title = "x".repeat(500), memo = "m".repeat(5000), cost = -5),
                ScheduleItem("b", day = -3, time = "9:5", title = " ", cost = Int.MAX_VALUE),
            ),
            travelers = listOf("", " A ", "B"),
        )
        val c = messy.sanitized(nowMs = 42)
        assertEquals("無題のしおり", c.title)
        // 開始日が読めなければ 2000-01-01。終了日(2026-10-01)は有効だが、日数の上限(60日)で止まる
        assertEquals("2000-01-01", c.startDate)
        assertEquals("2000-02-29", c.endDate)
        assertEquals(Limits.MAX_DAYS, c.dayCount())
        // 範囲外の日(99 や -3)は、最初の日〜最終日(59日目)に収める
        assertEquals(listOf(Limits.MAX_DAYS - 1, 0), c.items.map { it.day })
        assertEquals(listOf("", "09:05"), c.items.map { it.time })
        assertEquals(Limits.MAX_SHORT, c.items[0].title.length)
        assertEquals(Limits.MAX_TEXT, c.items[0].memo.length)
        assertEquals("(無題)", c.items[1].title)
        assertEquals(listOf(0, Limits.MAX_COST), c.items.map { it.cost })
        assertEquals(listOf("A", "B"), c.travelers)
        assertEquals(42, c.updatedAtMs)
    }
}
