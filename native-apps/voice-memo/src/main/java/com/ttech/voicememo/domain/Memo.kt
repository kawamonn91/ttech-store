package com.ttech.voicememo.domain

import kotlinx.serialization.Serializable

@Serializable
data class Memo(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val text: String,
)

/**
 * 画面に出す文字起こし。確定した文([committed])の後ろに、認識途中の文([partial])をつなげる。
 * 日本語は単語の間に空白を入れないので、そのまま連結する。
 */
fun liveTranscript(committed: String, partial: String): String = committed + partial

/** 入力値からメモを作る。空なら作らない(null)。前後の空白は除く。 */
fun buildMemo(id: String, date: String, text: String): Memo? =
    text.trim().takeIf { it.isNotEmpty() }?.let { Memo(id, date, it) }

/** 全メモを書き出すテキスト(Webアプリ版と同じ「[日付]\n本文」を空行区切り)。 */
fun exportText(memos: List<Memo>): String = memos.joinToString("\n\n") { "[${it.date}]\n${it.text}" }
