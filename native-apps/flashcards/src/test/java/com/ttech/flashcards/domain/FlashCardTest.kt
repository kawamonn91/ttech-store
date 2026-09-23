package com.ttech.flashcards.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashCardTest {

    @Test
    fun `表と裏がどちらも埋まっていれば有効`() {
        assertTrue(isValidCard("apple", "りんご"))
    }

    @Test
    fun `表か裏が空なら無効`() {
        assertFalse(isValidCard("", "りんご"))
        assertFalse(isValidCard("apple", ""))
        assertFalse(isValidCard("  ", "りんご"))
    }

    @Test
    fun `次のカードは末尾の次で先頭に戻る`() {
        assertEquals(1, nextIndex(0, size = 3))
        assertEquals(2, nextIndex(1, size = 3))
        assertEquals(0, nextIndex(2, size = 3))
    }

    @Test
    fun `前のカードは先頭の前で末尾に戻る`() {
        assertEquals(2, prevIndex(0, size = 3))
        assertEquals(0, prevIndex(1, size = 3))
    }

    @Test
    fun `カードが1枚なら常に同じインデックス`() {
        assertEquals(0, nextIndex(0, size = 1))
        assertEquals(0, prevIndex(0, size = 1))
    }
}
