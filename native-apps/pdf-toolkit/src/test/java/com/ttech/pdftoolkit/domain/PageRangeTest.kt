package com.ttech.pdftoolkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeTest {

    @Test
    fun `結合は2つ以上のPDFが必要`() {
        assertFalse(canMerge(0))
        assertFalse(canMerge(1))
        assertTrue(canMerge(2))
    }

    @Test
    fun `抽出はPDFを選び、ページ指定が空でないときだけできる`() {
        assertTrue(canExtract(hasFile = true, range = "1-3"))
        assertFalse(canExtract(hasFile = false, range = "1-3"))
        assertFalse(canExtract(hasFile = true, range = "  "))
    }

    @Test
    fun `単一ページの指定は0始まりの番号になる`() {
        assertEquals(listOf(4), parsePageRange("5", pageCount = 10))
    }

    @Test
    fun `範囲指定と単一指定を組み合わせられる`() {
        assertEquals(listOf(0, 1, 2, 4), parsePageRange("1-3,5", pageCount = 10))
    }

    @Test
    fun `総ページ数を超える分は切り捨てる`() {
        assertEquals(listOf(8, 9), parsePageRange("9-20", pageCount = 10))
        assertEquals(emptyList<Int>(), parsePageRange("11", pageCount = 10))
    }

    @Test
    fun `書式に合わない部分は無視される`() {
        assertEquals(listOf(0, 2), parsePageRange("1, abc, 3, -, 2-x", pageCount = 10))
    }

    @Test
    fun `空白や空の要素があっても読み取れる`() {
        assertEquals(listOf(0, 1), parsePageRange(" 1 ,, 2 ", pageCount = 5))
    }

    @Test
    fun `逆順の範囲は空になる`() {
        assertEquals(emptyList<Int>(), parsePageRange("5-3", pageCount = 10))
    }

    @Test
    fun `0ページ目の指定は無効として捨てる`() {
        assertEquals(emptyList<Int>(), parsePageRange("0", pageCount = 10))
        assertEquals(listOf(0, 1), parsePageRange("0-2", pageCount = 10))
    }

    @Test
    fun `同じページを続けて指定すれば重複して並ぶ`() {
        assertEquals(listOf(0, 0, 1), parsePageRange("1,1-2", pageCount = 3))
    }

    @Test
    fun `極端に大きい数字でもあふれずに扱える`() {
        assertEquals(emptyList<Int>(), parsePageRange("99999999999999999999", pageCount = 10))
        assertEquals(listOf(9), parsePageRange("10", pageCount = 10))
    }
}
