package com.ttech.cookingunits.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CookingUnitsTest {
    @Test
    fun `カップ1杯は200ml`() {
        assertEquals(200.0, CookingUnits.toMl(1.0, "カップ(200ml)"), 0.001)
    }

    @Test
    fun `大さじ2杯は30ml`() {
        assertEquals(30.0, CookingUnits.toMl(2.0, "大さじ(15ml)"), 0.001)
    }

    @Test
    fun `小さじ0杯は0ml`() {
        assertEquals(0.0, CookingUnits.toMl(0.0, "小さじ(5ml)"), 0.001)
    }

    @Test
    fun `mlはそのまま`() {
        assertEquals(123.0, CookingUnits.toMl(123.0, "ml"), 0.001)
    }

    @Test
    fun `未知の単位はmlとして扱う`() {
        assertEquals(5.0, CookingUnits.toMl(5.0, "存在しない単位"), 0.001)
    }

    @Test
    fun `水は密度1なのでmlと同じ重さ`() {
        assertEquals(200.0, CookingUnits.toGrams(200.0, "水"), 0.001)
    }

    @Test
    fun `小麦粉は密度0点55`() {
        assertEquals(110.0, CookingUnits.toGrams(200.0, "小麦粉(薄力粉)"), 0.001)
    }

    @Test
    fun `はちみつは密度1点4`() {
        assertEquals(280.0, CookingUnits.toGrams(200.0, "はちみつ"), 0.001)
    }

    @Test
    fun `重さは小数第1位に丸められる`() {
        // 15ml(大さじ1) * 0.9(バター) = 13.5g
        assertEquals(13.5, CookingUnits.toGrams(15.0, "バター"), 0.001)
    }

    @Test
    fun `未知の材料は密度1として扱う`() {
        assertEquals(200.0, CookingUnits.toGrams(200.0, "存在しない材料"), 0.001)
    }
}
