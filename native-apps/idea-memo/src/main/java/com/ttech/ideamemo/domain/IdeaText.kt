package com.ttech.ideamemo.domain

fun stars(count: Int): String = "★".repeat(count.coerceIn(0, 3))

/** 1件を、LINEなどに貼れる短い文章にする */
fun Idea.toShareText(): String = buildString {
    append("【アプリのアイデア】").append(title).append('\n')
    append(stars(priority)).append(' ').append(category.label).append(" ・ ").append(status.label).append('\n')
    if (oneLiner.isNotEmpty()) append(oneLiner).append('\n')
    if (memo.isNotEmpty()) append(memo).append('\n')
    if (reference.isNotEmpty()) append("きっかけ: ").append(reference).append('\n')
}.trimEnd() + "\n"

private fun Idea.listLine(): String {
    val head = "${stars(priority)} $title"
    val tail = listOf(category.label, oneLiner).filter { it.isNotEmpty() }.joinToString(" ・ ")
    return if (tail.isEmpty()) head else "$head($tail)"
}

/** リスト全体を、状態ごとにまとめた文章にする */
fun ideasText(ideas: List<Idea>): String {
    val out = StringBuilder("【アプリのアイデア帳】").append(ideas.size).append("件\n")
    fun section(title: String, list: List<Idea>) {
        if (list.isEmpty()) return
        out.append('\n').append("■").append(title).append('\n')
        list.forEach { out.append(it.listLine()).append('\n') }
    }
    val byPriority = compareByDescending<Idea> { it.priority }.thenBy { it.title }
    for (status in Status.entries) {
        section(status.label, ideas.filter { it.status == status }.sortedWith(byPriority))
    }
    return out.toString().trimEnd() + "\n"
}

/** 最初に開いたときに見てもらう、使い方の分かるサンプル */
fun sampleIdeas(newId: () -> String, nowMs: Long): List<Idea> {
    var t = nowMs
    fun idea(title: String, oneLiner: String, category: Category, priority: Int, memo: String = "", status: Status = Status.IDEA, reference: String = "") =
        Idea(id = newId(), title = title, oneLiner = oneLiner, memo = memo, category = category, priority = priority, status = status, reference = reference, createdAtMs = t--, updatedAtMs = nowMs)
    return listOf(
        idea("習慣トラッカー", "毎日のチェックだけで、続いている日数が積み上がる", Category.HEALTH, 3, "禁煙・筋トレ・日記など、複数の習慣を同時に管理できると便利そう。"),
        idea("レシート家計簿", "レシートを撮るだけで、費目ごとの支出が分かる", Category.FINANCE, 2, "文字認識(OCR)が必要そうで難易度は高め。まずは手入力版から。", status = Status.CONSIDERING),
        idea("英単語の耳学習", "通勤中に、音声だけで英単語を復習できる", Category.EDUCATION, 2, reference = "他の単語アプリは画面を見る前提のものが多い"),
        idea("旅のしおり", "旅行の予定・持ち物・費用を1つにまとめて共有できる", Category.HOBBY, 3, "すでに作った。次はこのアプリ自体をアイデア帳の実例として残す。", status = Status.BUILT),
        idea("会議メモの整理", "話した内容から、決定事項とToDoだけを抜き出す", Category.BUSINESS, 1, status = Status.REJECTED, memo = "音声認識の精度に依存しすぎて、個人開発では厳しそう。"),
    )
}
