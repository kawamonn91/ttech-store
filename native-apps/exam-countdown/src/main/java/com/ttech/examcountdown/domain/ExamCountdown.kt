package com.ttech.examcountdown.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class StudySession(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val minutes: Int,
)

/**
 * 試験日までの残り日数(Webアプリ版と同じロジック)。
 * 試験日を過ぎていれば負の値(経過日数)になる。
 */
fun daysUntil(examDateIso: String, todayIso: String): Long {
    val today = LocalDate.parse(todayIso)
    val target = LocalDate.parse(examDateIso)
    return ChronoUnit.DAYS.between(today, target)
}

/** 累計学習時間(分)。 */
fun totalMinutes(sessions: List<StudySession>): Int = sessions.sumOf { it.minutes }

/** 「◯時間◯分」形式の表示文字列。 */
fun formatHoursAndMinutes(totalMinutes: Int): String = "${totalMinutes / 60}時間${totalMinutes % 60}分"

/** タイマーの表示文字列(mm:ss)。 */
fun formatElapsed(elapsedSeconds: Long): String {
    val m = elapsedSeconds / 60
    val s = elapsedSeconds % 60
    return "%02d:%02d".format(m, s)
}
