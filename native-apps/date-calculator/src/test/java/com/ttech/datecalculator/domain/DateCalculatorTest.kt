package com.ttech.datecalculator.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateCalculatorTest {
    @Test
    fun `offsetは正の向きで未来の日付を返す`() {
        val result = DateCalculator.offset(LocalDate.of(2026, 1, 1), 30, 1)
        assertEquals(LocalDate.of(2026, 1, 31), result)
    }

    @Test
    fun `offsetは負の向きで過去の日付を返す`() {
        val result = DateCalculator.offset(LocalDate.of(2026, 1, 31), 30, -1)
        assertEquals(LocalDate.of(2026, 1, 1), result)
    }

    @Test
    fun `年齢は誕生日を迎えていれば単純な年差`() {
        val age = DateCalculator.age(birth = LocalDate.of(2000, 1, 1), asOf = LocalDate.of(2026, 1, 1))
        assertEquals(26, age)
    }

    @Test
    fun `年齢は誕生日前なら1歳引く`() {
        val age = DateCalculator.age(birth = LocalDate.of(2000, 12, 31), asOf = LocalDate.of(2026, 1, 1))
        assertEquals(25, age)
    }

    @Test
    fun `年齢はうるう年生まれの2月29日でも計算できる`() {
        val age = DateCalculator.age(birth = LocalDate.of(2000, 2, 29), asOf = LocalDate.of(2026, 3, 1))
        assertEquals(26, age)
    }

    @Test
    fun `daysBetweenは開始日と終了日が同じなら0`() {
        val d = LocalDate.of(2026, 1, 1)
        assertEquals(0L, DateCalculator.daysBetween(d, d))
    }

    @Test
    fun `daysBetweenは終了日が前なら負の値`() {
        val result = DateCalculator.daysBetween(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1))
        assertEquals(-9L, result)
    }

    @Test
    fun `daysBetweenはうるう年をまたいでも正しい`() {
        val result = DateCalculator.daysBetween(LocalDate.of(2026, 2, 28), LocalDate.of(2028, 3, 1))
        assertEquals(732L, result)
    }
}
