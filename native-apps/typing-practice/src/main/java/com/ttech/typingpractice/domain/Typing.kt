package com.ttech.typingpractice.domain

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

val SENTENCES = listOf(
    "きょうはいいてんきですね",
    "にほんごのタイピングれんしゅうです",
    "つづけることがじょうずになるひけつです",
    "あさごはんはしっかりたべましょう",
    "しごとがおわったらゆっくりやすみたい",
    "でんしゃがおくれているようです",
    "あたらしいほんをよみはじめました",
    "こんしゅうまつはさんぽにいきたい",
)

const val MAX_RESULTS = 10

data class TypingResult(val wpm: Int, val accuracy: Int)

/** 直前と同じ文が続けて出ないように選ぶ。 */
fun pickSentence(exclude: String? = null, random: Random = Random): String =
    SENTENCES.filter { it != exclude }.random(random)

/** 5文字を1語とみなした1分あたりの語数。開始時刻が無い場合は0.01分として扱う(Web版と同じ)。 */
fun calcWpm(chars: Int, elapsedMillis: Long?): Int {
    val elapsedMin = if (elapsedMillis == null) 0.01 else elapsedMillis / 60000.0
    return (chars / 5.0 / max(elapsedMin, 0.01)).roundToInt()
}

fun calcAccuracy(input: String, target: String): Int {
    if (target.isEmpty()) return 0
    val correct = target.indices.count { input.getOrNull(it) == target[it] }
    return (correct * 100.0 / target.length).roundToInt()
}

/** 入力が課題文と一致したときだけ結果を返す。 */
fun evaluate(input: String, target: String, startedAt: Long?, now: Long): TypingResult? {
    if (input != target) return null
    val elapsed = startedAt?.let { now - it }
    return TypingResult(wpm = calcWpm(target.length, elapsed), accuracy = calcAccuracy(input, target))
}

/** 新しい結果を先頭に足し、直近[MAX_RESULTS]件だけ残す。 */
fun List<TypingResult>.withNewResult(result: TypingResult): List<TypingResult> =
    (listOf(result) + this).take(MAX_RESULTS)

fun List<TypingResult>.averageWpm(): Int? =
    if (isEmpty()) null else (sumOf { it.wpm } / size.toDouble()).roundToInt()
