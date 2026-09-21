package com.ttech.colorpalette.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPaletteTest {
    // 期待値はWebのmicrosaas版(app/page.tsx)と同じJavaScript実装で算出し、一致を確認したもの。

    @Test
    fun `赤はHSLで色相0・彩度100・明度50`() {
        val (h, s, l) = ColorPalette.hexToHsl("#ff0000")
        assertEquals(0.0, h, 0.01)
        assertEquals(100.0, s, 0.01)
        assertEquals(50.0, l, 0.01)
    }

    @Test
    fun `白は彩度0・明度100`() {
        val (h, s, l) = ColorPalette.hexToHsl("#ffffff")
        assertEquals(0.0, h, 0.01)
        assertEquals(0.0, s, 0.01)
        assertEquals(100.0, l, 0.01)
    }

    @Test
    fun `黒は彩度0・明度0`() {
        val (_, s, l) = ColorPalette.hexToHsl("#000000")
        assertEquals(0.0, s, 0.01)
        assertEquals(0.0, l, 0.01)
    }

    @Test
    fun `HSLからHEXへ相互変換できる(赤)`() {
        assertEquals("#ff0000", ColorPalette.hslToHex(0.0, 100.0, 50.0))
    }

    @Test
    fun `HSLからHEXへ相互変換できる(白と黒)`() {
        assertEquals("#ffffff", ColorPalette.hslToHex(0.0, 0.0, 100.0))
        assertEquals("#000000", ColorPalette.hslToHex(0.0, 0.0, 0.0))
    }

    @Test
    fun `補色配色は5色でベースカラーと反対色を含む`() {
        val colors = ColorPalette.generate("#2563eb", Harmony.COMPLEMENTARY)
        assertEquals(listOf("#0e3b9c", "#2563eb", "#82a6f4", "#ebad25", "#f4d082"), colors)
    }

    @Test
    fun `類似色配色`() {
        val colors = ColorPalette.generate("#2563eb", Harmony.ANALOGOUS)
        assertEquals(listOf("#25c6eb", "#2594eb", "#2563eb", "#2531eb", "#4a25eb"), colors)
    }

    @Test
    fun `三色配色`() {
        val colors = ColorPalette.generate("#2563eb", Harmony.TRIADIC)
        assertEquals(listOf("#1043b3", "#2563eb", "#eb2563", "#63eb25", "#95f16b"), colors)
    }

    @Test
    fun `モノクロマティック配色`() {
        val colors = ColorPalette.generate("#2563eb", Harmony.MONOCHROMATIC)
        assertEquals(listOf("#b1c7f8", "#6b95f1", "#2563eb", "#1043b3", "#0a296d"), colors)
    }

    @Test
    fun `配色はどのルールでも5色になる`() {
        for (harmony in Harmony.entries) {
            assertEquals(5, ColorPalette.generate("#00ff88", harmony).size)
        }
    }

    @Test
    fun `HEX形式のバリデーション`() {
        assertTrue(isValidHex("#2563eb"))
        assertTrue(isValidHex("#FFFFFF"))
        assertFalse(isValidHex("2563eb"))
        assertFalse(isValidHex("#fff"))
        assertFalse(isValidHex("#gggggg"))
        assertFalse(isValidHex(""))
    }
}
