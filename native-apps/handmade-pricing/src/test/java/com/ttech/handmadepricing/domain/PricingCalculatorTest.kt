package com.ttech.handmadepricing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingCalculatorTest {

    @Test
    fun `材料名と原価がどちらも有効なら有効`() {
        assertTrue(isValidMaterial("布", 500))
    }

    @Test
    fun `材料名が空または原価0なら無効`() {
        assertFalse(isValidMaterial("", 500))
        assertFalse(isValidMaterial("  ", 500))
        assertFalse(isValidMaterial("布", 0))
    }

    @Test
    fun `デフォルト条件での推奨価格は概算式どおり`() {
        // 材料費500円、制作60分・時給1500円、利益率30%、手数料10%
        val result = calcPricing(
            PricingInput(
                materials = listOf(Material("1", "布", 500)),
                workMinutes = 60,
                hourlyWage = 1500,
                marginPercent = 30,
                feePercent = 10,
            ),
        )
        // 原価 = 500 + 1500 = 2000円、上乗せ後 = 2600円
        assertEquals(2000, result.baseCost)
        // 推奨価格 = ceil(2600 / 0.9 / 10) * 10 = 2890円
        assertEquals(2890, result.suggestedPrice)
    }

    @Test
    fun `材料費が複数あれば合計される`() {
        val result = calcPricing(
            PricingInput(
                materials = listOf(Material("1", "布", 500), Material("2", "糸", 200)),
                workMinutes = 0,
                hourlyWage = 0,
                marginPercent = 0,
                feePercent = 0,
            ),
        )
        assertEquals(700, result.materialTotal)
        assertEquals(700, result.baseCost)
    }

    @Test
    fun `手数料100%以上なら手数料計算を行わず上乗せ後価格をそのまま使う`() {
        val result = calcPricing(
            PricingInput(
                materials = listOf(Material("1", "布", 1000)),
                workMinutes = 0,
                hourlyWage = 0,
                marginPercent = 0,
                feePercent = 100,
            ),
        )
        assertEquals(1000, result.suggestedPrice)
    }

    @Test
    fun `制作時間や材料がなければ原価も推奨価格も0円`() {
        val result = calcPricing(
            PricingInput(materials = emptyList(), workMinutes = 0, hourlyWage = 0, marginPercent = 0, feePercent = 0),
        )
        assertEquals(0, result.baseCost)
        assertEquals(0, result.suggestedPrice)
    }
}
