package com.ttech.travelwishlist.domain

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WishLogicTest {
    private fun place(
        id: String,
        name: String = id,
        region: String = "",
        category: Category = Category.OTHER,
        priority: Int = 2,
        months: List<Int> = emptyList(),
        status: Status = Status.WANT,
        budget: Int = 0,
        created: Long = 0,
        rating: Int = 0,
        country: String = "",
        memo: String = "",
    ) = Place(id = id, name = name, region = region, country = country, category = category, priority = priority, months = months, status = status, budget = budget, createdAtMs = created, rating = rating, memo = memo)

    @Test
    fun `月の表示は、連続する月をまとめる`() {
        assertEquals("", monthsLabel(emptyList()))
        assertEquals("3月", monthsLabel(listOf(3)))
        assertEquals("3〜5月", monthsLabel(listOf(5, 3, 4)))
        assertEquals("3〜4月・10〜11月", monthsLabel(listOf(3, 4, 10, 11)))
        assertEquals("1月・3月", monthsLabel(listOf(1, 3)))
        assertEquals("通年", monthsLabel((1..12).toList()))
    }

    @Test
    fun `月の表示は、年をまたぐ範囲を1つにまとめる`() {
        assertEquals("11〜1月", monthsLabel(listOf(11, 12, 1)))
        assertEquals("11〜2月", monthsLabel(listOf(1, 2, 11, 12)))
        assertEquals("6月・11〜1月", monthsLabel(listOf(6, 11, 12, 1)))
        // 範囲外・重複は無視
        assertEquals("3月", monthsLabel(listOf(3, 3, 0, 13)))
    }

    @Test
    fun `今月から何か月先か`() {
        assertEquals(0, monthsUntil(listOf(9), 9))
        assertEquals(2, monthsUntil(listOf(11), 9))
        assertEquals(4, monthsUntil(listOf(1), 9)) // 9月 → 翌年1月
        assertEquals(1, monthsUntil(listOf(1, 10), 9))
        assertNull(monthsUntil(emptyList(), 9))
    }

    @Test
    fun `絞り込み 状態・カテゴリ・キーワード(名前・所在地・国・メモ)`() {
        val list = listOf(
            place("a", "草津温泉", "群馬県", Category.ONSEN, status = Status.WANT, memo = "湯畑"),
            place("b", "パリ", "海外", Category.CITY, status = Status.PLANNING, country = "フランス"),
            place("c", "奈良公園", "奈良県", Category.CULTURE, status = Status.VISITED),
        )
        assertEquals(listOf("a", "b", "c"), filterPlaces(list, "", null, null).map { it.id })
        assertEquals(listOf("b"), filterPlaces(list, "", Status.PLANNING, null).map { it.id })
        assertEquals(listOf("a"), filterPlaces(list, "", null, Category.ONSEN).map { it.id })
        assertEquals(listOf("a"), filterPlaces(list, "群馬", null, null).map { it.id })
        assertEquals(listOf("b"), filterPlaces(list, "フランス", null, null).map { it.id })
        assertEquals(listOf("a"), filterPlaces(list, " 湯畑 ", null, null).map { it.id })
        assertEquals(emptyList<String>(), filterPlaces(list, "京都", null, null).map { it.id })
        assertEquals(listOf("c"), filterPlaces(list, "", Status.VISITED, Category.CULTURE).map { it.id })
    }

    @Test
    fun `検索は英字の大文字小文字を区別しない`() {
        val list = listOf(place("a", "Kusatsu Onsen"))
        assertEquals(listOf("a"), filterPlaces(list, "kUSATSU", null, null).map { it.id })
    }

    @Test
    fun `並べ替え`() {
        val list = listOf(
            place("low", "い", priority = 1, months = listOf(12), created = 3),
            place("high-old", "ろ", priority = 3, months = listOf(9), created = 1),
            place("high-new", "は", priority = 3, months = emptyList(), created = 5),
            place("mid", "に", priority = 2, months = listOf(10), created = 4),
        )
        assertEquals(listOf("high-new", "high-old", "mid", "low"), sortPlaces(list, SortOrder.PRIORITY, 9).map { it.id })
        assertEquals(listOf("high-new", "mid", "low", "high-old"), sortPlaces(list, SortOrder.NEWEST, 9).map { it.id })
        // 今月(9月)に近い順。時期が未設定のものは最後
        assertEquals(listOf("high-old", "mid", "low", "high-new"), sortPlaces(list, SortOrder.SEASON, 9).map { it.id })
        assertEquals(listOf("い", "に", "は", "ろ"), sortPlaces(list, SortOrder.NAME, 9).map { it.name })
    }

    @Test
    fun `今月おすすめは、今月が行きたい時期で、まだ行っていないもの`() {
        val list = listOf(
            place("a", priority = 1, months = listOf(9, 10)),
            place("b", priority = 3, months = listOf(9)),
            place("c", priority = 3, months = listOf(9), status = Status.VISITED),
            place("d", priority = 3, months = listOf(5)),
            place("e", priority = 2, months = listOf(9), status = Status.PLANNING),
        )
        assertEquals(listOf("b", "e", "a"), recommendedThisMonth(list, 9).map { it.id })
        assertTrue(recommendedThisMonth(list, 2).isEmpty())
    }

    @Test
    fun `ランダムは、行った場所を選ばない。候補が無ければnull`() {
        val list = listOf(place("a", status = Status.VISITED), place("b"))
        repeat(50) { assertEquals("b", pickRandom(list, 9, Random(it))?.id) }
        assertNull(pickRandom(listOf(place("a", status = Status.VISITED)), 9, Random(1)))
        assertNull(pickRandom(emptyList(), 9, Random(1)))
    }

    @Test
    fun `ランダムは、行きたい度に比例した確率で選ぶ(今月が時期なら2倍)`() {
        val list = listOf(
            place("p1", priority = 1),
            place("p3", priority = 3),
            place("p1season", priority = 1, months = listOf(9)),
        )
        // 重み: 1 / 3 / 2(合計6)
        val counts = mutableMapOf<String, Int>()
        val random = Random(42)
        repeat(6000) { counts.merge(pickRandom(list, 9, random)!!.id, 1, Int::plus) }
        assertTrue(counts.toString(), counts.getValue("p1") in 800..1200)       // 約 1000
        assertTrue(counts.toString(), counts.getValue("p3") in 2800..3200)      // 約 3000
        assertTrue(counts.toString(), counts.getValue("p1season") in 1800..2200) // 約 2000
    }

    @Test
    fun `行った にする・取り消す`() {
        val p = place("a", priority = 3)
        val visited = p.markVisited(LocalDate.of(2026, 8, 10), "  最高だった  ", 9, nowMs = 7)
        assertEquals(Status.VISITED, visited.status)
        assertEquals("2026-08-10", visited.visitedDate)
        assertEquals("最高だった", visited.impression)
        assertEquals(5, visited.rating) // 上限で止める
        assertEquals(7, visited.updatedAtMs)
        val back = visited.reopen(nowMs = 8)
        assertEquals(Status.WANT, back.status)
        assertEquals("最高だった", back.impression) // 感想は残す
        assertEquals(Status.PLANNING, visited.reopen(Status.PLANNING, 9).status)
    }

    @Test
    fun `統計 都道府県の制覇・海外・予算・評価`() {
        val list = listOf(
            place("a", region = "京都府", status = Status.VISITED, rating = 5),
            place("b", region = "京都府", status = Status.VISITED, rating = 3),
            place("c", region = "奈良県", status = Status.WANT, budget = 20000),
            place("d", region = "京都府", status = Status.PLANNING, budget = 30000), // 京都は行ったので「行きたい」には数えない
            place("e", region = "海外", country = "フランス", status = Status.VISITED),
            place("f", region = "海外", country = "フランス", status = Status.VISITED),
            place("g", region = "海外", country = "タイ", status = Status.VISITED),
            place("h", region = "", status = Status.WANT, category = Category.ONSEN),
        )
        val s = computeStats(list)
        assertEquals(8, s.total)
        assertEquals(2, s.want)
        assertEquals(1, s.planning)
        assertEquals(5, s.visited)
        assertEquals(setOf("京都府"), s.visitedPrefectures)
        assertEquals(setOf("奈良県"), s.wantedPrefectures)
        assertEquals(2, s.visitedOverseas) // フランス・タイ
        assertEquals(50000, s.budgetToGo)
        assertEquals(4.0, s.averageRating!!, 0.001)
        assertEquals(1.0 / 47, s.prefectureRate, 0.0001)
        assertEquals(1, s.byCategory[Category.ONSEN])
    }

    @Test
    fun `統計 何もなければ平均評価はnull`() {
        val s = computeStats(emptyList())
        assertEquals(0, s.total)
        assertNull(s.averageRating)
        assertEquals(0.0, s.prefectureRate, 0.0)
    }

    @Test
    fun `都道府県は47。県・府・都を付けない入力を正式名にそろえる`() {
        assertEquals(47, Japan.PREFECTURES.size)
        assertEquals(47, Japan.PREFECTURES.toSet().size)
        assertEquals("京都府", Japan.normalize("京都"))
        assertEquals("東京都", Japan.normalize(" 東京 "))
        assertEquals("北海道", Japan.normalize("北海道"))
        assertEquals("大阪府", Japan.normalize("大阪府"))
        assertEquals("海外", Japan.normalize("海外"))
        assertEquals("パリ", Japan.normalize("パリ"))
        assertEquals("", Japan.normalize(""))
    }

    @Test
    fun `整形 範囲外の値・URL・日付を直す`() {
        val messy = Place(
            id = " x ", name = " ", region = "京都", priority = 9, months = listOf(13, 3, 3, 0, 12), budget = -1,
            url = "javascript:alert(1)", visitedDate = "でたらめ", rating = 99, memo = "m".repeat(5000),
        ).sanitized(nowMs = 5)
        assertEquals("x", messy.id)
        assertEquals("(無題)", messy.name)
        assertEquals("京都府", messy.region)
        assertEquals(3, messy.priority)
        assertEquals(listOf(3, 12), messy.months)
        assertEquals(0, messy.budget)
        assertEquals("", messy.url) // http(s)以外は保存しない
        assertEquals("", messy.visitedDate)
        assertEquals(5, messy.rating)
        assertEquals(Limits.MAX_TEXT, messy.memo.length)
        assertEquals(5, messy.updatedAtMs)
        assertEquals("https://example.com/a", messy.copy(url = " https://example.com/a ").sanitized().url)
        assertEquals(1, messy.copy(priority = 0).sanitized().priority)
    }

    @Test
    fun `所在地の表示`() {
        assertEquals("京都府", place("a", region = "京都府").locationLabel())
        assertEquals("海外・フランス", place("a", region = "海外", country = "フランス").locationLabel())
        assertEquals("海外", place("a", region = "海外").locationLabel())
        assertEquals("", place("a").locationLabel())
    }

    @Test
    fun `金額の表示は3桁区切り`() {
        assertEquals("¥0", yen(0))
        assertEquals("¥300,000", yen(300_000))
    }

    @Test
    fun `都道府県の短い名前は、京都と北海道を崩さない`() {
        assertEquals("東京", Japan.shortName("東京都"))
        assertEquals("京都", Japan.shortName("京都府"))
        assertEquals("大阪", Japan.shortName("大阪府"))
        assertEquals("北海道", Japan.shortName("北海道"))
        assertEquals("沖縄", Japan.shortName("沖縄県"))
        // 全部の名前が、重複せず1文字以上で短くできる
        val shorts = Japan.PREFECTURES.map { Japan.shortName(it) }
        assertEquals(47, shorts.toSet().size)
        assertTrue(shorts.all { it.length >= 2 })
    }
}
