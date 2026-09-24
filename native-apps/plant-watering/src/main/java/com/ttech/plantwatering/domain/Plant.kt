package com.ttech.plantwatering.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val DEFAULT_INTERVAL_DAYS = 7

@Serializable
data class Plant(
    val id: String,
    val name: String,
    /** 水やりの間隔(日) */
    val intervalDays: Int,
    /** 最後に水やりした日 ISO8601 (YYYY-MM-DD) */
    val lastWateredDate: String,
)

/**
 * 入力値から植物を作る。名前が空なら作らない(null)。間隔が読めない・0以下なら7日(Webアプリ版と同じ)。
 * 登録した日を最終水やり日とする。
 */
fun buildPlant(id: String, name: String, intervalDays: String, today: String): Plant? {
    if (name.isBlank()) return null
    val interval = intervalDays.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_INTERVAL_DAYS
    return Plant(id, name.trim(), interval, today)
}

/** 最終水やり日から今日までの経過日数。 */
fun Plant.daysSince(today: LocalDate): Long = ChronoUnit.DAYS.between(LocalDate.parse(lastWateredDate), today)

/** 次の水やりまでの残り日数(0以下なら水やりの時期)。 */
fun Plant.remainingDays(today: LocalDate): Long = intervalDays - daysSince(today)

fun Plant.isDue(today: LocalDate): Boolean = remainingDays(today) <= 0

/** 水やりが近い順(期限切れが長いものほど先頭)。 */
fun List<Plant>.sortedByUrgency(today: LocalDate): List<Plant> = sortedBy { it.remainingDays(today) }

fun List<Plant>.waterNow(id: String, today: String): List<Plant> =
    map { if (it.id == id) it.copy(lastWateredDate = today) else it }

/** 状態の表示(「あと3日」/「水やりの時期です(2日超過)」)。 */
fun Plant.statusLabel(today: LocalDate): String {
    val remaining = remainingDays(today)
    return if (remaining <= 0) "水やりの時期です(${-remaining}日超過)" else "あと${remaining}日"
}
