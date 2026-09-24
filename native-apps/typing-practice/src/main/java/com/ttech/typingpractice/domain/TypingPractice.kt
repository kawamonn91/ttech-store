package com.ttech.typingpractice.domain

import kotlin.math.roundToInt
import kotlin.random.Random

/** 出題する文(Webアプリ版と同じ)。 */
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

/** 記録として残す直近の回数(Webアプリ版と同じ10回)。 */
const val MAX_RESULTS = 10

/** 直前と同じ文を続けて出さないように、[exclude] 以外からランダムに選ぶ。 */
fun pickSentence(exclude: String? = null, random: Random = Random.Default): String {
    val candidates = SENTENCES.filter { it != exclude }
    return candidates.random(random)
}

data class TypingResult(val wpm: Int, val accuracy: Int)

/**
 * 1文を打ち終えたときの結果。WPMは「5文字=1語」として計算し、経過時間は最低0.01分とみなす
 * (Webアプリ版と同じ計算)。
 */
fun calcResult(target: String, input: String, elapsedMillis: Long): TypingResult {
    val elapsedMin = maxOf(elapsedMillis / 60_000.0, 0.01)
    val wpm = (target.length / 5.0 / elapsedMin).roundToInt()
    val correct = target.indices.count { i -> input.getOrNull(i) == target[i] }
    val accuracy = if (target.isEmpty()) 0 else (correct.toDouble() / target.length * 100).roundToInt()
    return TypingResult(wpm, accuracy)
}

enum class CharState { PENDING, CORRECT, WRONG }

/** お手本の各文字の表示状態(未入力/正しい/間違い)。 */
fun charStates(target: String, input: String): List<CharState> = target.mapIndexed { i, ch ->
    when {
        i >= input.length -> CharState.PENDING
        input[i] == ch -> CharState.CORRECT
        else -> CharState.WRONG
    }
}

/** 新しい結果を先頭に足し、直近 [MAX_RESULTS] 回分だけ残す。 */
fun List<TypingResult>.addResult(result: TypingResult): List<TypingResult> = (listOf(result) + this).take(MAX_RESULTS)

/** 平均WPM(四捨五入)。記録がなければnull。 */
fun List<TypingResult>.averageWpm(): Int? = if (isEmpty()) null else (sumOf { it.wpm }.toDouble() / size).roundToInt()
