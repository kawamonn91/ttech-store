package com.ttech.warikan.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WarikanTest {
    @Test
    fun `割り切れる場合は端数0`() {
        assertEquals(WarikanResult(2500, 0), Warikan.calculate(10000, 4, RoundingMode.UP))
    }

    @Test
    fun `切り上げは幹事の取り分が減る方向の端数になる`() {
        // 10000 / 3 = 3333.33... -> 切り上げ 3334 * 3 = 10002 -> 端数 -2
        val r = Warikan.calculate(10000, 3, RoundingMode.UP)
        assertEquals(3334, r.perPerson)
        assertEquals(-2, r.remainder)
    }

    @Test
    fun `切り捨ては幹事の取り分が増える方向の端数になる`() {
        // 10000 / 3 -> 切り捨て 3333 * 3 = 9999 -> 端数 +1
        val r = Warikan.calculate(10000, 3, RoundingMode.DOWN)
        assertEquals(3333, r.perPerson)
        assertEquals(1, r.remainder)
    }

    @Test
    fun `四捨五入`() {
        // 10000 / 3 = 3333.33 -> 四捨五入 3333
        assertEquals(3333, Warikan.calculate(10000, 3, RoundingMode.NEAREST).perPerson)
        // 10001 / 2 = 5000.5 -> 四捨五入 5001 (Kotlin roundToInt は .5 を切り上げ)
        assertEquals(5001, Warikan.calculate(10001, 2, RoundingMode.NEAREST).perPerson)
    }

    @Test
    fun `人数0のときは全額が端数`() {
        assertEquals(WarikanResult(0, 10000), Warikan.calculate(10000, 0, RoundingMode.UP))
    }

    @Test
    fun `金額0のときは全員0円`() {
        assertEquals(WarikanResult(0, 0), Warikan.calculate(0, 4, RoundingMode.UP))
    }

    @Test
    fun `負の端数はマイナス表示になる想定の値を返す`() {
        val r = Warikan.calculate(100, 3, RoundingMode.UP)
        assertEquals(34, r.perPerson)
        assertEquals(-2, r.remainder)
    }
}
