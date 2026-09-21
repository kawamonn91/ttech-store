package com.ttech.qrgenerator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrGeneratorTest {
    @Test
    fun `空文字は生成しない`() {
        assertNull(QrGenerator.encode(""))
    }

    @Test
    fun `空白のみも生成しない`() {
        assertNull(QrGenerator.encode("   "))
    }

    @Test
    fun `URLから指定サイズの行列を生成する`() {
        val matrix = QrGenerator.encode("https://example.com", size = 280)
        assertTrue(matrix != null)
        // ZXingはmarginを含めた正方形になるよう指定サイズぴったりで返す
        assertEquals(280, matrix!!.width)
        assertEquals(280, matrix.height)
    }

    @Test
    fun `生成した行列には少なくとも1つ塗りつぶされたモジュールがある`() {
        val matrix = QrGenerator.encode("hello")!!
        var filled = false
        outer@ for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix.get(x, y)) {
                    filled = true
                    break@outer
                }
            }
        }
        assertTrue(filled)
    }

    @Test
    fun `ある程度長い文章でも生成できる`() {
        val longText = "あ".repeat(50)
        val matrix = QrGenerator.encode(longText, size = 280)
        assertTrue(matrix != null)
    }
}
