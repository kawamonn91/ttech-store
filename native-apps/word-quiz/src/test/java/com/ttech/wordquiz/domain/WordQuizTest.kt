package com.ttech.wordquiz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordQuizTest {
    @Test
    fun `すべての問題の正解は選択肢に含まれ選択肢に重複がない`() {
        Deck.entries.flatMap { it.words }.forEach { w ->
            assertTrue(w.question, w.answer in w.choices)
            assertEquals(w.question, w.choices.size, w.choices.toSet().size)
        }
    }

    @Test
    fun `最後の問題の次は最初に戻る`() {
        val deck = Deck.EIKEN
        assertEquals(deck.words[0], deck.questionAt(deck.words.size))
        assertEquals(deck.words[1], deck.questionAt(deck.words.size + 1))
    }

    @Test
    fun `並べ替えた選択肢は元と同じ要素で同じ問題番号なら同じ順`() {
        val w = Deck.KANJI.words[0]
        val shuffled = w.shuffledChoices(3)
        assertEquals(w.choices.toSet(), shuffled.toSet())
        assertEquals(shuffled, w.shuffledChoices(3))
    }

    @Test
    fun `正解が常に先頭にはならない`() {
        val firsts = (0 until 20).map { i -> Deck.KANJI.questionAt(i).let { it.shuffledChoices(i).first() == it.answer } }
        assertTrue(firsts.any { !it })
    }

    @Test
    fun `正解数はデッキごとに数える`() {
        val score = Score().increment(Deck.KANJI).increment(Deck.KANJI).increment(Deck.EIKEN)
        assertEquals(2, score.of(Deck.KANJI))
        assertEquals(1, score.of(Deck.EIKEN))
    }

    @Test
    fun `回答後の選択肢の表示状態`() {
        assertEquals(ChoiceState.NEUTRAL, choiceState("a", "a", null))
        assertEquals(ChoiceState.CORRECT, choiceState("a", "a", "b"))
        assertEquals(ChoiceState.WRONG_SELECTED, choiceState("b", "a", "b"))
        assertEquals(ChoiceState.NEUTRAL, choiceState("c", "a", "b"))
    }
}
