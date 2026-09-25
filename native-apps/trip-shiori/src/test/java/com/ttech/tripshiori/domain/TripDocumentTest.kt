package com.ttech.tripshiori.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDocumentTest {
    private var seq = 0
    private fun newId() = "id${++seq}"

    private val trip = Trip(
        id = "t", title = "京都の旅", destination = "京都", startDate = "2026-10-10", endDate = "2026-10-11",
        travelers = listOf("A", "B"),
        notes = "雨なら美術館",
        items = listOf(
            ScheduleItem("2", 0, "12:00", "ランチ", "祇園", "予約済み", ItemKind.MEAL, 3000),
            ScheduleItem("1", 0, "08:00", "出発", kind = ItemKind.MOVE, cost = 14000),
            ScheduleItem("3", 1, "", "自由行動", kind = ItemKind.OTHER),
        ),
        packing = listOf(PackingItem("p1", "財布", checked = true), PackingItem("p2", "傘")),
        lodgings = listOf(Lodging("l1", "河原町ホテル", "京都市中京区", "075-000-0000", "K-1", "15:00〜", "荷物OK")),
        contacts = listOf(Contact("c1", "フロント", "075-000-0000")),
    )

    @Test
    fun `テキストの共有 表紙・日ごとの予定(時刻順)・宿泊先・連絡先・持ち物・メモ・費用`() {
        val expected = """
            【旅のしおり】京都の旅
            京都  2026/10/10(土)〜10/11(日)(1泊2日)
            メンバー: A・B

            ■1日目 10/10(土)
            08:00 出発(移動)
            　費用: ¥14,000
            12:00 ランチ(食事)
            　場所: 祇園
            　予約済み
            　費用: ¥3,000

            ■2日目 10/11(日)
            ・ 自由行動(その他)

            ■宿泊先
            ・ 河原町ホテル
            　住所: 京都市中京区
            　電話: 075-000-0000
            　15:00〜
            　予約番号: K-1
            　荷物OK

            ■連絡先
            ・ フロント
            　電話: 075-000-0000

            ■持ち物
            ☑ 財布
            □ 傘

            ■メモ
            雨なら美術館

            ■費用の目安
            合計 ¥17,000(1人あたり ¥8,500・2人)
        """.trimIndent() + "\n"
        assertEquals(expected, trip.toShareText())
    }

    @Test
    fun `空の区分は出さない。予定が1件も無い日は、その旨を書く`() {
        val t = Trip(id = "t", title = "空", startDate = "2026-10-10", endDate = "2026-10-10")
        val text = t.toShareText()
        assertTrue(text.contains("予定はまだありません"))
        for (heading in listOf("宿泊先", "連絡先", "持ち物", "メモ", "費用")) assertFalse(heading, text.contains("■$heading"))
    }

    @Test
    fun `1人だけなら、1人あたりは出さない`() {
        val text = trip.copy(travelers = listOf("A")).toShareText()
        assertTrue(text.contains("合計 ¥17,000\n"))
        assertFalse(text.contains("1人あたり"))
    }

    @Test
    fun `サンプルのしおりは、そのまま使える内容になっている`() {
        val sample = sampleTrip(today = LocalDate.of(2026, 9, 25), newId = ::newId, nowMs = 1)
        // 2026-09-25 は金曜日。次の土曜日は 9/26
        assertEquals("2026-09-26", sample.startDate)
        assertEquals(3, sample.dayCount())
        assertTrue(sample.items.all { it.day in 0..2 })
        assertEquals(sample, sample.sanitized(nowMs = 1))
        assertEquals(sample.items.size + sample.packing.size + sample.lodgings.size + sample.contacts.size + 1, (sample.items.map { it.id } + sample.packing.map { it.id } + sample.lodgings.map { it.id } + sample.contacts.map { it.id } + sample.id).toSet().size)
    }

    @Test
    fun `サンプルの開始日は、今日が土曜なら来週の土曜`() {
        val sat = LocalDate.of(2026, 9, 26)
        assertEquals("2026-10-03", sampleTrip(sat, ::newId, 1).startDate)
        // 日曜(9/27)の次の土曜は 10/3
        assertEquals("2026-10-03", sampleTrip(LocalDate.of(2026, 9, 27), ::newId, 1).startDate)
    }

    @Test
    fun `持ち物のおすすめには重複がなく、すべて名前がある`() {
        for (p in PACKING_PRESETS) {
            assertTrue(p.title, p.items.isNotEmpty())
            assertEquals(p.title, p.items.size, p.items.toSet().size)
            assertTrue(p.items.all { it.isNotBlank() && it.length <= Limits.MAX_SHORT })
        }
    }
}
