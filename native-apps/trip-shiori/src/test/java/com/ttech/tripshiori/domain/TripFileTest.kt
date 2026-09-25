package com.ttech.tripshiori.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFileTest {
    private var seq = 0
    private fun newId() = "new${++seq}"

    private val trip = Trip(
        id = "t1", title = "京都の旅", destination = "京都", startDate = "2026-10-10", endDate = "2026-10-12",
        travelers = listOf("A", "B"), notes = "メモ",
        items = listOf(ScheduleItem("i1", 1, "09:00", "清水寺", "清水寺", "早めに", ItemKind.SIGHT, 400)),
        packing = listOf(PackingItem("p1", "傘", true)),
        lodgings = listOf(Lodging("l1", "ホテル", "住所", "075", "K-1", "15:00〜", "メモ")),
        contacts = listOf(Contact("c1", "フロント", "075", "")),
        updatedAtMs = 5,
    )

    private fun ok(text: String): Trip = (TripFile.decode(text, ::newId, 99) as TripFile.Decoded.Ok).trip
    private fun error(text: String): String = (TripFile.decode(text, ::newId, 99) as TripFile.Decoded.Error).message

    @Test
    fun `書き出したファイルを読み込むと、中身は同じで、idはすべて新しくなる`() {
        val loaded = ok(TripFile.encode(trip))
        assertEquals(trip.copy(id = loaded.id, items = trip.items.map { it.copy(id = loaded.items[0].id) }, packing = trip.packing.map { it.copy(id = loaded.packing[0].id) }, lodgings = trip.lodgings.map { it.copy(id = loaded.lodgings[0].id) }, contacts = trip.contacts.map { it.copy(id = loaded.contacts[0].id) }, updatedAtMs = 99), loaded)
        assertNotEquals("t1", loaded.id)
        assertTrue(loaded.id.startsWith("new"))
        assertEquals(setOf(loaded.id, loaded.items[0].id, loaded.packing[0].id, loaded.lodgings[0].id, loaded.contacts[0].id).size, 5)
    }

    @Test
    fun `ファイルの形式は名前と版を含む`() {
        val text = TripFile.encode(trip)
        assertTrue(text.contains("\"format\": \"ttech-tripshiori\""))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test
    fun `しおりのファイルでないものは、分かりやすいメッセージで断る`() {
        assertEquals("しおりのファイルではありません", error("hello"))
        assertEquals("しおりのファイルではありません", error("{}"))
        assertEquals("しおりのファイルではありません", error("""{"format":"other","version":1,"trip":null}"""))
        assertEquals("しおりのファイルではありません", error("""{"format":"ttech-tripshiori","version":1}"""))
        assertEquals("しおりのファイルではありません", error(""))
    }

    @Test
    fun `新しい版のファイルは、アプリの更新を案内する`() {
        val text = TripFile.encode(trip).replace("\"version\": 1", "\"version\": 2")
        assertTrue(error(text).contains("アプリを更新"))
    }

    @Test
    fun `大きすぎるファイルは読まない`() {
        assertEquals("ファイルが大きすぎます", error("x".repeat(TripFile.MAX_BYTES + 1)))
    }

    @Test
    fun `知らない項目や、知らない予定の種類があっても読める(新しい版で足された項目を無視する)`() {
        val text = """
            {"format":"ttech-tripshiori","version":1,"extra":1,
             "trip":{"id":"x","title":"未来の旅","startDate":"2026-10-10","endDate":"2026-10-10","weather":"晴れ",
               "items":[{"id":"a","day":0,"title":"予定","kind":"SPACE","photo":"a.png"}]}}
        """.trimIndent()
        val t = ok(text)
        assertEquals("未来の旅", t.title)
        assertEquals(ItemKind.SIGHT, t.items[0].kind) // 知らない種類は既定値
    }

    @Test
    fun `中身が壊れていても、読み込み後は必ず安全な形になる`() {
        val text = """
            {"format":"ttech-tripshiori","version":1,
             "trip":{"id":"x","title":"","startDate":"nonsense","endDate":"2026-13-45",
               "items":[{"id":"a","day":500,"time":"99:99","title":"x","cost":-1}]}}
        """.trimIndent()
        val t = ok(text)
        assertEquals("無題のしおり", t.title)
        assertEquals(1, t.dayCount())
        assertEquals(0, t.items[0].day)
        assertEquals("", t.items[0].time)
        assertEquals(0, t.items[0].cost)
    }

    @Test
    fun `ファイル名は、使えない文字を除いて拡張子をつける`() {
        assertEquals("京都の旅.shiori", TripFile.fileName(trip))
        assertEquals("a_b_c.shiori", TripFile.fileName(trip.copy(title = "a/b:c")))
        assertEquals("shiori.shiori", TripFile.fileName(trip.copy(title = "///")))
    }
}
