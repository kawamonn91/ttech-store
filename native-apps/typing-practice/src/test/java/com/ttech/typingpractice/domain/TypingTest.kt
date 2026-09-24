package com.ttech.typingpractice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class TypingTest {

    @Test
    fun `直前の文は次に選ばれない`() {
        repeat(100) {
            val exclude = SENTENCES.first()
            assertNotEquals(exclude, pickSentence(exclude, Random(it)))
        }
    }

    @Test
    fun `1分で50文字打てば10WPM`() {
        assertEquals(10, calcWpm(chars = 50, elapsedMillis = 60_000))
    }

    @Test
    fun `開始時刻が無い場合や極端に短い場合は0_01分として扱う`() {
        // 10文字 / 5 / 0.01分 = 200 WPM
        assertEquals(200, calcWpm(chars = 10, elapsedMillis = null))
        assertEquals(200, calcWpm(chars = 10, elapsedMillis = 1))
    }

    @Test
    fun `正確率は一致した文字の割合`() {
        assertEquals(100, calcAccuracy("あいうえ", "あいうえ"))
        assertEquals(75, calcAccuracy("あいうお", "あいうえ"))
        assertEquals(50, calcAccuracy("あい", "あいうえ"))
    }

    @Test
    fun `課題文と一致しない間は結果が出ない`() {
        assertNull(evaluate("あい", "あいうえ", startedAt = 0, now = 1000))
    }

    @Test
    fun `一致したときに速度と正確率が計算される`() {
        val result = evaluate("あいうえおかきくけこ", "あいうえおかきくけこ", startedAt = 0, now = 60_000)
        assertEquals(TypingResult(wpm = 2, accuracy = 100), result)
    }

    @Test
    fun `結果は新しい順で直近10件だけ残る`() {
        var results = emptyList<TypingResult>()
        for (i in 1..12) results = results.withNewResult(TypingResult(wpm = i, accuracy = 100))
        assertEquals(10, results.size)
        assertEquals(12, results.first().wpm)
        assertEquals(3, results.last().wpm)
    }

    @Test
    fun `平均速度は四捨五入され記録が無ければnull`() {
        assertNull(emptyList<TypingResult>().averageWpm())
        val results = listOf(TypingResult(10, 100), TypingResult(15, 100))
        assertEquals(13, results.averageWpm())
    }
}
