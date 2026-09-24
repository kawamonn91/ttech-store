package com.ttech.voicememo.domain

import kotlinx.serialization.Serializable

@Serializable
data class Memo(
    val id: String,
    /** 保存した日(ISO8601 YYYY-MM-DD)。 */
    val date: String,
    val text: String,
)

fun isValidMemoText(text: String): Boolean = text.isNotBlank()

/**
 * 音声認識の途中経過と確定結果をまとめて持つ。
 * 認識は区切りごとに確定して再開するので、確定済みの文字列([committed])に、
 * 今聞き取り中の途中経過([partial])を続けて表示する。日本語なので区切りに空白は入れない。
 */
data class Transcript(val committed: String = "", val partial: String = "") {
    val text: String get() = committed + partial

    fun withPartial(value: String) = copy(partial = value)

    fun withFinal(value: String) = Transcript(committed = committed + value, partial = "")

    /** 聞き取りが終わったとき、確定しなかった途中経過も結果として残す。 */
    fun flushed() = Transcript(committed = committed + partial, partial = "")
}

/** 全メモを書き出すテキスト(Web版と同じ「[日付]\n本文」を空行で区切った形式)。 */
fun exportText(memos: List<Memo>): String = memos.joinToString("\n\n") { "[${it.date}]\n${it.text}" }
