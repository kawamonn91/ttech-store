package com.ttech.travelwishlist.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class Category(val label: String) {
    ONSEN("温泉"),
    SCENERY("絶景"),
    GOURMET("グルメ"),
    CULTURE("歴史・文化"),
    OUTDOOR("アウトドア"),
    THEME("テーマパーク"),
    CITY("街歩き"),
    BEACH("海・島"),
    OTHER("その他"),
}

@Serializable
enum class Status(val label: String) {
    WANT("行きたい"),
    PLANNING("計画中"),
    VISITED("行った"),
}

/**
 * 行きたい場所1件。
 * [region] は都道府県名(「京都府」など)か [Japan.OVERSEAS](海外)、未設定なら空。海外のときの国・地域は [country]。
 */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val region: String = "",
    val country: String = "",
    val category: Category = Category.OTHER,
    /** 行きたい度。1〜3(3が「どうしても行きたい」) */
    val priority: Int = 2,
    /** 行きたい時期(月。1〜12) */
    val months: List<Int> = emptyList(),
    /** 予算の目安(円)。0なら未設定 */
    val budget: Int = 0,
    val memo: String = "",
    /** 参考にしたページのURL(メモとして保存するだけ。このアプリは通信しない) */
    val url: String = "",
    val status: Status = Status.WANT,
    /** 行った日(ISO)。行っていなければ空 */
    val visitedDate: String = "",
    val impression: String = "",
    /** 行った感想の評価。0(未評価)〜5 */
    val rating: Int = 0,
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
)

val WishJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

/** 都道府県(地方ごと) */
object Japan {
    const val OVERSEAS = "海外"

    val REGIONS: List<Pair<String, List<String>>> = listOf(
        "北海道" to listOf("北海道"),
        "東北" to listOf("青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県"),
        "関東" to listOf("茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県"),
        "中部" to listOf("新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県"),
        "近畿" to listOf("三重県", "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県"),
        "中国" to listOf("鳥取県", "島根県", "岡山県", "広島県", "山口県"),
        "四国" to listOf("徳島県", "香川県", "愛媛県", "高知県"),
        "九州・沖縄" to listOf("福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"),
    )

    val PREFECTURES: List<String> = REGIONS.flatMap { it.second }

    /** 一覧に並べるときの短い名前。「東京都」→「東京」、「京都府」→「京都」、「北海道」はそのまま */
    fun shortName(prefecture: String): String = when (prefecture) {
        "北海道", "京都府" -> prefecture.removeSuffix("府")
        else -> if (prefecture.length > 2 && prefecture.last() in "都府県") prefecture.dropLast(1) else prefecture
    }

    /** 「東京」「京都」のように、県・府・都を付けずに入力されたものを正式な名前にそろえる。分からなければそのまま */
    fun normalize(input: String): String {
        val t = input.trim()
        if (t == OVERSEAS) return t
        if (t in PREFECTURES) return t
        return PREFECTURES.firstOrNull { it.length > 2 && it.dropLast(1) == t } ?: t
    }
}
