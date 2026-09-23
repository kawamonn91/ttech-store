package com.ttech.furusatonozei.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationLimitCalculatorTest {

    @Test
    fun `年収0円なら限度額は0円`() {
        assertEquals(0, calcDonationLimit(CalcInput(salaryIncome = 0, hasSpouse = false, dependents = 0)))
    }

    @Test
    fun `年収600万円・独身・扶養なしの限度額は概算式どおり`() {
        val limit = calcDonationLimit(CalcInput(salaryIncome = 6_000_000, hasSpouse = false, dependents = 0))
        assertEquals(77_000, limit)
    }

    @Test
    fun `配偶者ありだと控除が増えるため限度額は下がる`() {
        val withoutSpouse = calcDonationLimit(CalcInput(salaryIncome = 6_000_000, hasSpouse = false, dependents = 0))
        val withSpouse = calcDonationLimit(CalcInput(salaryIncome = 6_000_000, hasSpouse = true, dependents = 0))
        assertTrue(withSpouse < withoutSpouse)
    }

    @Test
    fun `扶養家族が増えるほど限度額は下がる`() {
        val noDependents = calcDonationLimit(CalcInput(salaryIncome = 6_000_000, hasSpouse = false, dependents = 0))
        val twoDependents = calcDonationLimit(CalcInput(salaryIncome = 6_000_000, hasSpouse = false, dependents = 2))
        assertTrue(twoDependents < noDependents)
    }

    @Test
    fun `限度額は1000円単位で切り捨てられる`() {
        val limit = calcDonationLimit(CalcInput(salaryIncome = 5_270_000, hasSpouse = false, dependents = 1))
        assertEquals(0, limit % 1000)
    }

    @Test
    fun `給与所得控除は速算表の各区分で正しく計算される`() {
        assertEquals(550_000, employmentIncomeDeduction(1_000_000))
        assertEquals(1_950_000, employmentIncomeDeduction(10_000_000))
    }
}
