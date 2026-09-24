package com.ttech.wordquiz.domain

import kotlin.random.Random

data class WordEntry(val question: String, val answer: String, val choices: List<String>)

enum class Deck(val label: String, val words: List<WordEntry>) {
    KANJI(
        "漢字検定",
        listOf(
            WordEntry("「憂鬱」の読みは?", "ゆううつ", listOf("ゆううつ", "ゆうつ", "ゆういつ", "ゆうかつ")),
            WordEntry("「曖昧」の読みは?", "あいまい", listOf("あいまい", "あいみ", "あいび", "あいまん")),
            WordEntry("「杜撰」の読みは?", "ずさん", listOf("ずさん", "としん", "もりせん", "とせん")),
            WordEntry("「几帳面」の読みは?", "きちょうめん", listOf("きちょうめん", "きちょうづら", "きばりめん", "つくえめん")),
            WordEntry("「軋轢」の読みは?", "あつれき", listOf("あつれき", "きしれき", "そつれき", "あつわ")),
            WordEntry("「詭弁」の読みは?", "きべん", listOf("きべん", "けいべん", "きへん", "ぎべん")),
        ),
    ),
    EIKEN(
        "英検",
        listOf(
            WordEntry("\"ubiquitous\" の意味は?", "至る所にある", listOf("至る所にある", "非常に珍しい", "一時的な", "秘密の")),
            WordEntry("\"meticulous\" の意味は?", "細心の", listOf("細心の", "怠惰な", "寛大な", "曖昧な")),
            WordEntry("\"resilient\" の意味は?", "回復力のある", listOf("回復力のある", "脆弱な", "攻撃的な", "無関心な")),
            WordEntry("\"ambiguous\" の意味は?", "あいまいな", listOf("あいまいな", "明確な", "緊急の", "豊富な")),
            WordEntry("\"reluctant\" の意味は?", "気が進まない", listOf("気が進まない", "熱心な", "自信のある", "正確な")),
        ),
    ),
}

/** [index] 問目の問題(最後まで行ったら最初に戻る。Webアプリ版と同じ)。 */
fun Deck.questionAt(index: Int): WordEntry = words[index % words.size]

/**
 * 選択肢の表示順。Webアプリ版は常に正解が先頭だったため、問題番号から決まる順序で並べ替える
 * (同じ問題番号なら再描画しても順序が変わらない)。
 */
fun WordEntry.shuffledChoices(index: Int): List<String> = choices.shuffled(Random(index * 31 + question.hashCode()))

data class Score(val kanji: Int = 0, val eiken: Int = 0) {
    fun of(deck: Deck): Int = if (deck == Deck.KANJI) kanji else eiken

    fun increment(deck: Deck): Score = if (deck == Deck.KANJI) copy(kanji = kanji + 1) else copy(eiken = eiken + 1)
}

enum class ChoiceState { NEUTRAL, CORRECT, WRONG_SELECTED }

/** 回答後の選択肢の表示状態。正解は常に強調し、選んだ不正解は赤で示す。回答前はすべて通常表示。 */
fun choiceState(choice: String, answer: String, selected: String?): ChoiceState = when {
    selected == null -> ChoiceState.NEUTRAL
    choice == answer -> ChoiceState.CORRECT
    choice == selected -> ChoiceState.WRONG_SELECTED
    else -> ChoiceState.NEUTRAL
}
