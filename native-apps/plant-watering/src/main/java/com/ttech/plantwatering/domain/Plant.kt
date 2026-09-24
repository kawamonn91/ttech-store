package com.ttech.plantwatering.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

const val DEFAULT_INTERVAL_DAYS = 7

/** 水やりのお知らせを出す時刻(毎日この時刻に、水やりの時期の植物があるか確認する)。 */
const val REMINDER_HOUR = 9

@Serializable
data class Plant(
    val id: String,
    val name: String,
    val intervalDays: Int,
    /** 最後に水やりした日(ISO8601 YYYY-MM-DD)。 */
    val lastWateredDate: String,
)

fun isValidPlantName(name: String): Boolean = name.isNotBlank()

/** 入力が空・0・数字でない場合は7日に戻す(Web版の `Number(value) || 7` と同じ)。 */
fun normalizeIntervalDays(input: Int?): Int = if (input == null || input == 0) DEFAULT_INTERVAL_DAYS else input

fun daysSinceWatered(plant: Plant, today: LocalDate): Int =
    ChronoUnit.DAYS.between(LocalDate.parse(plant.lastWateredDate), today).toInt()

/** 次の水やりまであと何日か。0以下なら水やりの時期(負の値は超過日数)。 */
fun remainingDays(plant: Plant, today: LocalDate): Int = plant.intervalDays - daysSinceWatered(plant, today)

fun isDue(plant: Plant, today: LocalDate): Boolean = remainingDays(plant, today) <= 0

/** 水やりが近い(または超過している)順に並べる。同じ日数なら登録の並びを保つ。 */
fun List<Plant>.sortedByUrgency(today: LocalDate): List<Plant> = sortedBy { remainingDays(it, today) }

fun Plant.wateredOn(today: LocalDate): Plant = copy(lastWateredDate = today.toString())

/** 水やりの時期になっている植物の名前(通知の本文に使う)。 */
fun List<Plant>.dueNames(today: LocalDate): List<String> = filter { isDue(it, today) }.map { it.name }

/** [now] より後で、次に来る毎日[REMINDER_HOUR]時。 */
fun nextReminderTime(now: ZonedDateTime, hour: Int = REMINDER_HOUR): ZonedDateTime {
    val todayAtHour = now.with(LocalTime.of(hour, 0))
    return if (todayAtHour.isAfter(now)) todayAtHour else todayAtHour.plusDays(1)
}
