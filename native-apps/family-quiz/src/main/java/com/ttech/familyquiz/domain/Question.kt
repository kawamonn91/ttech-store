package com.ttech.familyquiz.domain

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val question: String,
    val choices: List<String>,
    val correctIndex: Int,
)

/** 問題文と4つの選択肢がすべて埋まっているかを確認する(Webアプリ版と同じ検証)。 */
fun isValidQuestion(question: String, choices: List<String>): Boolean =
    question.isNotBlank() && choices.size == 4 && choices.all { it.isNotBlank() }

/** 選んだ選択肢が正解かどうか。 */
fun Question.isCorrect(selectedIndex: Int): Boolean = selectedIndex == correctIndex

/** 一連の回答からスコア(正解数)を計算する。 */
fun score(questions: List<Question>, answers: List<Int>): Int =
    questions.zip(answers).count { (q, a) -> q.isCorrect(a) }
