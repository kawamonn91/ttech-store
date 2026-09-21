package com.ttech.datecalculator.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 日付計算(Webのmicrosaas版と同じ3モード: ○日後/前・年齢計算・経過日数)の純粋ロジック */
object DateCalculator {
    /** [base] から [days] 日、[direction] の向き(+1: 後, -1: 前)にずらした日付 */
    fun offset(base: LocalDate, days: Int, direction: Int): LocalDate = base.plusDays(days.toLong() * direction)

    /** [asOf] 時点での満年齢。誕生日を迎えていなければ1歳引く */
    fun age(birth: LocalDate, asOf: LocalDate): Int {
        var age = asOf.year - birth.year
        val hadBirthdayThisYear = asOf.monthValue > birth.monthValue ||
            (asOf.monthValue == birth.monthValue && asOf.dayOfMonth >= birth.dayOfMonth)
        if (!hadBirthdayThisYear) age -= 1
        return age
    }

    /** [start] から [end] までの経過日数(終了日が前なら負の値になる) */
    fun daysBetween(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end)
}
