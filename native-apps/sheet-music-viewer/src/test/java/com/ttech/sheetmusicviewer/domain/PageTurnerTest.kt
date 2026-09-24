package com.ttech.sheetmusicviewer.domain

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTurnerTest {
    private val score = Score(pages = listOf("p1", "p2", "p3"))

    @Test
    fun `次へ・前へでページが移動する`() {
        val second = score.turn(PageTurn.NEXT)
        assertEquals(1, second.index)
        assertEquals(0, second.turn(PageTurn.PREV).index)
    }

    @Test
    fun `最初と最後のページより先には進まない`() {
        assertEquals(0, score.turn(PageTurn.PREV).index)
        val last = score.copy(index = 2)
        assertEquals(2, last.turn(PageTurn.NEXT).index)
        assertTrue(last.isLast)
        assertTrue(score.isFirst)
        assertFalse(score.isLast)
    }

    @Test
    fun `矢印・スペース・PageUpDownでめくれる`() {
        listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
            .forEach { assertEquals(PageTurn.NEXT, pageTurnFor(it)) }
        listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP)
            .forEach { assertEquals(PageTurn.PREV, pageTurnFor(it)) }
        assertNull(pageTurnFor(KeyEvent.KEYCODE_A))
    }

    @Test
    fun `ページ表示`() {
        assertEquals("2 / 3", score.copy(index = 1).pageLabel())
    }
}
