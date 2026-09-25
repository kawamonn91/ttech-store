package com.ttech.travelwishlist.domain

import java.time.LocalDate

fun stars(count: Int): String = "★".repeat(count.coerceIn(0, 5))

/** 1件を、LINEなどに貼れる短い文章にする */
fun Place.toShareText(): String = buildString {
    append("【行きたい場所】").append(name).append('\n')
    val where = listOf(locationLabel(), category.label).filter { it.isNotEmpty() }.joinToString("・")
    if (where.isNotEmpty()) append(where).append('\n')
    if (months.isNotEmpty()) append("行きたい時期: ").append(monthsLabel(months)).append('\n')
    if (budget > 0) append("予算の目安: ").append(yen(budget)).append('\n')
    if (memo.isNotEmpty()) append(memo).append('\n')
    if (status == Status.VISITED) {
        val date = parseDateOrNull(visitedDate)
        append("行きました").append(if (date != null) "(${date.year}/${date.monthValue}/${date.dayOfMonth})" else "").append('\n')
        if (rating > 0) append(stars(rating)).append('\n')
        if (impression.isNotEmpty()) append(impression).append('\n')
    }
    if (url.isNotEmpty()) append(url).append('\n')
}.trimEnd() + "\n"

private fun Place.listLine(): String {
    val head = "${stars(priority)} $name"
    val tail = listOf(locationLabel(), category.label).filter { it.isNotEmpty() }.joinToString("・")
    val extras = buildList {
        if (months.isNotEmpty()) add(monthsLabel(months))
        if (budget > 0) add("予算${yen(budget)}")
    }
    return head + (if (tail.isNotEmpty()) "($tail)" else "") + (if (extras.isNotEmpty()) " " + extras.joinToString(" ") else "")
}

/** リスト全体を、状態ごと(計画中・行きたい・行った)にまとめた文章にする */
fun wishlistText(places: List<Place>): String {
    val out = StringBuilder("【行きたい旅メモ】").append(places.size).append("件\n")
    fun section(title: String, list: List<Place>, line: (Place) -> String) {
        if (list.isEmpty()) return
        out.append('\n').append("■").append(title).append('\n')
        list.forEach { out.append(line(it)).append('\n') }
    }
    val byPriority = compareByDescending<Place> { it.priority }.thenBy { it.name }
    section("計画中", places.filter { it.status == Status.PLANNING }.sortedWith(byPriority)) { it.listLine() }
    section("行きたい", places.filter { it.status == Status.WANT }.sortedWith(byPriority)) { it.listLine() }
    section("行った", places.filter { it.status == Status.VISITED }.sortedByDescending { it.visitedDate }) { p ->
        val date = parseDateOrNull(p.visitedDate)?.let { "${it.year}/${it.monthValue}/${it.dayOfMonth} " } ?: ""
        "✓ $date${p.name}" + (p.locationLabel().takeIf { it.isNotEmpty() }?.let { "($it)" } ?: "") + (if (p.rating > 0) " ${stars(p.rating)}" else "")
    }
    return out.toString().trimEnd() + "\n"
}

/** 「サンプルを追加」で作る、有名な旅先の例。使い方が分かるよう、状態・時期・評価をいろいろ入れてある */
fun samplePlaces(today: LocalDate, newId: () -> String, nowMs: Long): List<Place> {
    var t = nowMs
    fun place(name: String, region: String, category: Category, priority: Int, months: List<Int>, budget: Int, memo: String, status: Status = Status.WANT, country: String = "", visited: LocalDate? = null, impression: String = "", rating: Int = 0) =
        Place(
            id = newId(), name = name, region = region, country = country, category = category, priority = priority, months = months.sorted(),
            budget = budget, memo = memo, status = status, visitedDate = visited?.toString() ?: "", impression = impression, rating = rating,
            createdAtMs = t--, updatedAtMs = nowMs,
        )
    return listOf(
        place("草津温泉", "群馬県", Category.ONSEN, 3, listOf(11, 12, 1, 2), 40000, "湯畑のライトアップを見て、湯もみも体験したい。"),
        place("北海道の富良野・美瑛", "北海道", Category.SCENERY, 3, listOf(7, 8), 90000, "ラベンダー畑と青い池。レンタカーで回る。", Status.PLANNING),
        place("伏見稲荷大社", "京都府", Category.CULTURE, 2, listOf(4, 5, 10, 11), 30000, "千本鳥居を早朝に歩く。"),
        place("沖縄・古宇利島", "沖縄県", Category.BEACH, 2, listOf(5, 6, 7, 8, 9), 80000, "海の色が特別らしい。"),
        place("金沢の兼六園と近江町市場", "石川県", Category.CITY, 2, listOf(3, 4, 11), 50000, "海鮮丼と和菓子めぐり。"),
        place("白川郷", "岐阜県", Category.CULTURE, 1, listOf(1, 2), 45000, "冬のライトアップの日に泊まりたい。"),
        place("パリ", "海外", Category.CITY, 3, listOf(4, 5, 6, 9), 300000, "美術館めぐりとパン屋さん。", country = "フランス"),
        place("東京ディズニーシー", "千葉県", Category.THEME, 1, listOf(3, 4, 9), 20000, "平日に休みを取って行く。"),
        place("奈良公園と東大寺", "奈良県", Category.CULTURE, 2, listOf(10, 11), 25000, "", Status.VISITED, visited = today.minusMonths(5),
            impression = "鹿にせんべいをあげて、大仏の大きさに驚いた。次は春日大社も。", rating = 5),
        place("大阪のたこ焼きめぐり", "大阪府", Category.GOURMET, 2, listOf(), 15000, "", Status.VISITED, visited = today.minusMonths(11),
            impression = "道頓堀の食べ歩きが楽しかった。", rating = 4),
    )
}
