package com.ttech.travelwishlist.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.random.Random

object Limits {
    const val MAX_PLACES = 1000
    const val MAX_NAME = 80
    const val MAX_SHORT = 100
    const val MAX_TEXT = 2000
    const val MAX_URL = 500
    const val MAX_BUDGET = 100_000_000
}

fun parseDateOrNull(text: String): LocalDate? =
    try { LocalDate.parse(text) } catch (_: DateTimeParseException) { null }

fun yen(amount: Int): String = "¥" + String.format(Locale.US, "%,d", amount)

/** 場所の表示用の所在地。「京都府」「海外・フランス」「海外」など。未設定なら空 */
fun Place.locationLabel(): String = when {
    region == Japan.OVERSEAS -> if (country.isNotEmpty()) "海外・$country" else "海外"
    else -> region
}

// ---------------------------------------------------------------- 月の表示

/** 行きたい時期の表示。連続する月はまとめる(3,4,5 → 「3〜5月」、11,12,1 → 「11〜1月」)。全部の月なら「通年」 */
fun monthsLabel(months: List<Int>): String {
    val sorted = months.filter { it in 1..12 }.distinct().sorted()
    if (sorted.isEmpty()) return ""
    if (sorted.size == 12) return "通年"
    // 連続する月のまとまり
    val runs = mutableListOf<MutableList<Int>>()
    for (m in sorted) {
        if (runs.isNotEmpty() && runs.last().last() == m - 1) runs.last() += m else runs += mutableListOf(m)
    }
    // 12月と1月がつながっていれば、1つにまとめる(11〜1月)
    if (runs.size > 1 && runs.first().first() == 1 && runs.last().last() == 12) {
        val head = runs.removeAt(0)
        runs.last() += head
    }
    return runs.joinToString("・") { run ->
        if (run.size == 1) "${run.first()}月" else "${run.first()}〜${run.last()}月"
    }
}

/** 今月から見て、その時期が何か月先か(0=今月)。時期が未設定なら null */
fun monthsUntil(months: List<Int>, currentMonth: Int): Int? =
    months.filter { it in 1..12 }.minOfOrNull { (it - currentMonth + 12) % 12 }

// ---------------------------------------------------------------- 絞り込みと並べ替え

enum class SortOrder(val label: String) {
    PRIORITY("行きたい順"),
    NEWEST("追加が新しい順"),
    SEASON("行きたい時期が近い順"),
    NAME("名前順"),
}

fun filterPlaces(places: List<Place>, query: String, status: Status?, category: Category?): List<Place> {
    val q = query.trim().lowercase()
    return places.filter { p ->
        (status == null || p.status == status) &&
            (category == null || p.category == category) &&
            (q.isEmpty() || listOf(p.name, p.region, p.country, p.memo, p.impression).any { it.lowercase().contains(q) })
    }
}

fun sortPlaces(places: List<Place>, order: SortOrder, currentMonth: Int): List<Place> = when (order) {
    SortOrder.PRIORITY -> places.sortedWith(compareByDescending<Place> { it.priority }.thenByDescending { it.createdAtMs })
    SortOrder.NEWEST -> places.sortedByDescending { it.createdAtMs }
    SortOrder.SEASON -> places.sortedWith(
        compareBy<Place> { monthsUntil(it.months, currentMonth) ?: Int.MAX_VALUE }.thenByDescending { it.priority },
    )
    SortOrder.NAME -> places.sortedBy { it.name }
}

/** 今月おすすめ: 今月が「行きたい時期」に入っていて、まだ行っていない場所。行きたい度の高い順 */
fun recommendedThisMonth(places: List<Place>, currentMonth: Int): List<Place> =
    places.filter { it.status != Status.VISITED && currentMonth in it.months }
        .sortedWith(compareByDescending<Place> { it.priority }.thenBy { it.name })

/**
 * 「次どこ行く?」の抽選。まだ行っていない場所(行きたい・計画中)から、行きたい度に比例した確率で1つ選ぶ。
 * 今月が行きたい時期に入っていれば、確率を2倍にする。候補が無ければ null。
 */
fun pickRandom(places: List<Place>, currentMonth: Int, random: Random): Place? {
    val candidates = places.filter { it.status != Status.VISITED }
    if (candidates.isEmpty()) return null
    val weights = candidates.map { p -> p.priority.coerceIn(1, 3) * (if (currentMonth in p.months) 2 else 1) }
    var r = random.nextInt(weights.sum())
    for ((i, w) in weights.withIndex()) {
        if (r < w) return candidates[i]
        r -= w
    }
    return candidates.last()
}

// ---------------------------------------------------------------- 状態の変更

/** 「行った」にする。日付・感想・評価を記録する */
fun Place.markVisited(date: LocalDate, impression: String, rating: Int, nowMs: Long): Place = copy(
    status = Status.VISITED,
    visitedDate = date.toString(),
    impression = impression.trim().take(Limits.MAX_TEXT),
    rating = rating.coerceIn(0, 5),
    updatedAtMs = nowMs,
)

/** 「行った」を取り消して、行きたいに戻す(感想などは残す) */
fun Place.reopen(status: Status = Status.WANT, nowMs: Long): Place = copy(status = status, updatedAtMs = nowMs)

// ---------------------------------------------------------------- 統計

data class Stats(
    val total: Int,
    val want: Int,
    val planning: Int,
    val visited: Int,
    /** 行った都道府県 */
    val visitedPrefectures: Set<String>,
    /** 行きたい・計画中の都道府県(まだ行っていない) */
    val wantedPrefectures: Set<String>,
    val visitedOverseas: Int,
    val byCategory: Map<Category, Int>,
    /** 行きたい・計画中の予算の目安の合計 */
    val budgetToGo: Int,
    val averageRating: Double?,
) {
    val prefectureRate: Double get() = visitedPrefectures.size.toDouble() / Japan.PREFECTURES.size
}

fun computeStats(places: List<Place>): Stats {
    val visited = places.filter { it.status == Status.VISITED }
    val toGo = places.filter { it.status != Status.VISITED }
    val visitedPrefs = visited.map { it.region }.filter { it in Japan.PREFECTURES }.toSet()
    val rated = visited.filter { it.rating > 0 }
    return Stats(
        total = places.size,
        want = places.count { it.status == Status.WANT },
        planning = places.count { it.status == Status.PLANNING },
        visited = visited.size,
        visitedPrefectures = visitedPrefs,
        wantedPrefectures = toGo.map { it.region }.filter { it in Japan.PREFECTURES }.toSet() - visitedPrefs,
        visitedOverseas = visited.filter { it.region == Japan.OVERSEAS }.map { it.country.ifEmpty { it.name } }.toSet().size,
        byCategory = places.groupingBy { it.category }.eachCount(),
        budgetToGo = toGo.sumOf { it.budget.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        averageRating = if (rated.isEmpty()) null else rated.sumOf { it.rating }.toDouble() / rated.size,
    )
}

// ---------------------------------------------------------------- 整形(保存・読み込みしても安全な形に)

fun Place.sanitized(nowMs: Long = updatedAtMs): Place {
    fun String.clip(max: Int = Limits.MAX_SHORT) = trim().take(max)
    val url = url.trim().take(Limits.MAX_URL).takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: ""
    return Place(
        id = id.clip(80),
        name = name.clip(Limits.MAX_NAME).ifEmpty { "(無題)" },
        region = Japan.normalize(region).clip(),
        country = country.clip(),
        category = category,
        priority = priority.coerceIn(1, 3),
        months = months.filter { it in 1..12 }.distinct().sorted(),
        budget = budget.coerceIn(0, Limits.MAX_BUDGET),
        memo = memo.clip(Limits.MAX_TEXT),
        url = url,
        status = status,
        visitedDate = visitedDate.takeIf { parseDateOrNull(it) != null } ?: "",
        impression = impression.clip(Limits.MAX_TEXT),
        rating = rating.coerceIn(0, 5),
        createdAtMs = createdAtMs,
        updatedAtMs = nowMs,
    )
}
