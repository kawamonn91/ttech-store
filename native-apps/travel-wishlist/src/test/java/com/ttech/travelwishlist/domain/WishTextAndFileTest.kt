package com.ttech.travelwishlist.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishTextAndFileTest {
    private var seq = 0
    private fun newId() = "id${++seq}"

    private val kusatsu = Place(
        id = "a", name = "草津温泉", region = "群馬県", category = Category.ONSEN, priority = 3,
        months = listOf(11, 12, 1, 2), budget = 40000, memo = "湯畑のライトアップ", url = "https://example.com/kusatsu",
    )
    private val paris = Place(id = "b", name = "パリ", region = "海外", country = "フランス", category = Category.CITY, priority = 2, status = Status.PLANNING)
    private val nara = Place(
        id = "c", name = "奈良公園", region = "奈良県", category = Category.CULTURE, status = Status.VISITED,
        visitedDate = "2026-04-05", impression = "鹿がかわいい", rating = 4,
    )

    @Test
    fun `1件の共有テキスト 行きたい場所`() {
        val expected = """
            【行きたい場所】草津温泉
            群馬県・温泉
            行きたい時期: 11〜2月
            予算の目安: ¥40,000
            湯畑のライトアップ
            https://example.com/kusatsu
        """.trimIndent() + "\n"
        assertEquals(expected, kusatsu.toShareText())
    }

    @Test
    fun `1件の共有テキスト 行った場所は日付・評価・感想が付く`() {
        val expected = """
            【行きたい場所】奈良公園
            奈良県・歴史・文化
            行きました(2026/4/5)
            ★★★★
            鹿がかわいい
        """.trimIndent() + "\n"
        assertEquals(expected, nara.toShareText())
    }

    @Test
    fun `リスト全体の共有テキスト 計画中・行きたい・行ったの順`() {
        val expected = """
            【行きたい旅メモ】3件

            ■計画中
            ★★ パリ(海外・フランス・街歩き)

            ■行きたい
            ★★★ 草津温泉(群馬県・温泉) 11〜2月 予算¥40,000

            ■行った
            ✓ 2026/4/5 奈良公園(奈良県) ★★★★
        """.trimIndent() + "\n"
        assertEquals(expected, wishlistText(listOf(nara, kusatsu, paris)))
    }

    @Test
    fun `空のリストの共有テキスト`() {
        assertEquals("【行きたい旅メモ】0件\n", wishlistText(emptyList()))
    }

    @Test
    fun `サンプルは全部が整っていて、状態・時期・評価が揃っている`() {
        val samples = samplePlaces(LocalDate.of(2026, 9, 25), ::newId, nowMs = 1000)
        assertEquals(10, samples.size)
        assertEquals(samples.size, samples.map { it.id }.toSet().size)
        assertEquals(samples, samples.map { it.sanitized(nowMs = 1000) })
        assertEquals(setOf(Status.WANT, Status.PLANNING, Status.VISITED), samples.map { it.status }.toSet())
        assertTrue(samples.filter { it.status == Status.VISITED }.all { it.visitedDate.isNotEmpty() && it.rating > 0 })
        assertTrue(samples.any { it.region == Japan.OVERSEAS })
        // 追加した順に、新しいものが大きい createdAt
        assertEquals(samples.sortedByDescending { it.createdAtMs }, samples)
    }

    @Test
    fun `メモファイルを書き出して読み込むと、中身は同じで、idは新しくなる`() {
        val places = listOf(kusatsu, paris, nara)
        val decoded = WishFile.decode(WishFile.encode(places), ::newId, nowMs = 99) as WishFile.Decoded.Ok
        assertEquals(places.map { it.name }, decoded.places.map { it.name })
        // 月は 1〜12 の順に整えられる(11,12,1,2 → 1,2,11,12)
        assertEquals(places.map { it.copy(id = "", updatedAtMs = 0, months = it.months.sorted()) }, decoded.places.map { it.copy(id = "", updatedAtMs = 0) })
        assertEquals(3, decoded.places.map { it.id }.toSet().size)
        assertFalse(decoded.places.any { it.id in setOf("a", "b", "c") })
        assertTrue(decoded.places.all { it.updatedAtMs == 99L })
    }

    @Test
    fun `メモファイルでないものは断る`() {
        for (text in listOf("hello", "{}", "", """{"format":"other","version":1,"places":[]}""", """{"format":"ttech-travelwishlist","version":1}""")) {
            assertEquals(text, "旅メモのファイルではありません", (WishFile.decode(text, ::newId, 1) as WishFile.Decoded.Error).message)
        }
    }

    @Test
    fun `新しい版・大きすぎるファイルは断る`() {
        val newer = WishFile.encode(listOf(kusatsu)).replace("\"version\": 1", "\"version\": 2")
        assertTrue((WishFile.decode(newer, ::newId, 1) as WishFile.Decoded.Error).message.contains("アプリを更新"))
        assertEquals("ファイルが大きすぎます", (WishFile.decode("x".repeat(WishFile.MAX_BYTES + 1), ::newId, 1) as WishFile.Decoded.Error).message)
    }

    @Test
    fun `知らない項目・知らない種類があっても読める。中身は整えられる`() {
        val text = """
            {"format":"ttech-travelwishlist","version":1,"extra":true,"places":[
              {"id":"x","name":"未来の場所","category":"SPACE","status":"TELEPORTED","priority":50,"url":"ftp://x","photo":"a.png","months":[99,4]}
            ]}
        """.trimIndent()
        val p = (WishFile.decode(text, ::newId, 1) as WishFile.Decoded.Ok).places.single()
        assertEquals("未来の場所", p.name)
        assertEquals(Category.OTHER, p.category) // 知らない種類は既定値
        assertEquals(Status.WANT, p.status)
        assertEquals(3, p.priority)
        assertEquals("", p.url)
        assertEquals(listOf(4), p.months)
    }

    @Test
    fun `読み込む件数には上限がある`() {
        val many = List(Limits.MAX_PLACES + 50) { kusatsu.copy(id = "p$it") }
        val decoded = WishFile.decode(WishFile.encode(many), ::newId, 1) as WishFile.Decoded.Ok
        assertEquals(Limits.MAX_PLACES, decoded.places.size)
    }
}
