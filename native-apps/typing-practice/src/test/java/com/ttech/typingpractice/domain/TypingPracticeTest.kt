package com.ttech.typingpractice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TypingPracticeTest {
    @Test
    fun `直前と同じ文は続けて出ない`() {
        val random = Random(42)
        repeat(100) {
            val prev = SENTENCES[it % SENTENCES.size]
            val next = pickSentence(prev, random)
            assertNotEquals(prev, next)
            assertTrue(next in SENTENCES)
        }
    }

    @Test
    fun `WPMは5文字を1語として計算する`() {
        // 10文字を30秒 → 2語 / 0.5分 = 4 WPM
        assertEquals(TypingResult(4, 100), calcResult("あいうえおかきくけこ", "あいうえおかきくけこ", 30_000))
    }

    @Test
    fun `経過時間は最低0点01分とみなす`() {
        assertEquals(200, calcResult("あいうえおかきくけこ", "あいうえおかきくけこ", 0).wpm)
    }

    @Test
    fun `正確率は一致した文字の割合`() {
        assertEquals(75, calcResult("あいうえ", "あいうお", 60_000).accuracy)
    }

    @Test
    fun `各文字の表示状態`() {
        assertEquals(
            listOf(CharState.CORRECT, CharState.WRONG, CharState.PENDING),
            charStates("あいう", "あか"),
        )
    }

    @Test
    fun `記録は新しい順に10回分だけ残す`() {
        var results = emptyList<TypingResult>()
        (1..12).forEach { results = results.addResult(TypingResult(it, 100)) }
        assertEquals(10, results.size)
        assertEquals(12, results.first().wpm)
        assertEquals(3, results.last().wpm)
    }

    @Test
    fun `平均WPMは四捨五入し記録がなければnull`() {
        assertEquals(35, listOf(TypingResult(30, 100), TypingResult(39, 100)).averageWpm())
        assertNull(emptyList<TypingResult>().averageWpm())
    }
}
