package com.ttech.familyquiz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionTest {
    private fun question(correctIndex: Int = 0) = Question(
        id = "1",
        question = "好きな食べ物は?",
        choices = listOf("寿司", "ラーメン", "カレー", "パスタ"),
        correctIndex = correctIndex,
    )

    @Test
    fun `問題文と4つの選択肢が埋まっていれば有効`() {
        assertTrue(isValidQuestion("問題", listOf("a", "b", "c", "d")))
    }

    @Test
    fun `問題文が空なら無効`() {
        assertFalse(isValidQuestion("", listOf("a", "b", "c", "d")))
        assertFalse(isValidQuestion("   ", listOf("a", "b", "c", "d")))
    }

    @Test
    fun `選択肢が1つでも空なら無効`() {
        assertFalse(isValidQuestion("問題", listOf("a", "", "c", "d")))
    }

    @Test
    fun `選択肢が4つでなければ無効`() {
        assertFalse(isValidQuestion("問題", listOf("a", "b", "c")))
    }

    @Test
    fun `正解のインデックスを選ぶと正解になる`() {
        val q = question(correctIndex = 2)
        assertTrue(q.isCorrect(2))
        assertFalse(q.isCorrect(0))
    }

    @Test
    fun `スコアは正解数の合計`() {
        val questions = listOf(question(correctIndex = 0), question(correctIndex = 1), question(correctIndex = 2))
        val answers = listOf(0, 0, 2) // 1問目正解、2問目不正解、3問目正解
        assertEquals(2, score(questions, answers))
    }

    @Test
    fun `全問不正解なら0点`() {
        val questions = listOf(question(correctIndex = 0), question(correctIndex = 1))
        assertEquals(0, score(questions, listOf(1, 0)))
    }
}
