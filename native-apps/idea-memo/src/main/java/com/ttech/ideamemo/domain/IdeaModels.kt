package com.ttech.ideamemo.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** T-tech Store のカテゴリ分けに合わせてある(思いついたアイデアが、そのままどこに出せそうか分かるように) */
@Serializable
enum class Category(val label: String) {
    PRODUCTIVITY("仕事効率化"),
    HEALTH("健康・ライフログ"),
    FINANCE("家計・お金"),
    EDUCATION("学習・スキル"),
    FAMILY("子育て・家族"),
    HOBBY("趣味・ホビー"),
    TOOLS("ツール"),
    BUSINESS("ビジネス"),
    ENTERTAINMENT("エンタメ"),
    OTHER("その他"),
}

@Serializable
enum class Status(val label: String) {
    IDEA("思いつき"),
    CONSIDERING("検討中"),
    PLANNED("作る予定"),
    BUILT("作った"),
    REJECTED("見送り"),
}

/** アプリのアイデア1件 */
@Serializable
data class Idea(
    val id: String,
    val title: String,
    /** 一言で言うと(触れ込み)。例: 「レシートを撮るだけで家計簿になる」 */
    val oneLiner: String = "",
    val memo: String = "",
    val category: Category = Category.OTHER,
    val status: Status = Status.IDEA,
    /** 作りたい度。1〜3(3が「絶対作りたい」) */
    val priority: Int = 2,
    /** きっかけ・参考にした既存アプリやページ(自由記述。URLでなくてもよい) */
    val reference: String = "",
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
)

val IdeaJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}
