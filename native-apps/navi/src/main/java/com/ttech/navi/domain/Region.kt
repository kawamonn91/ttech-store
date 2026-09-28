package com.ttech.navi.domain

/** いまいる都道府県・市区町村。[city] が分からないときは null */
data class Region(val prefCode: Int, val prefecture: String, val city: String?)

/**
 * 国土地理院の市区町村コード表(assets/muni.csv。「コード,都道府県名,市区町村名」)。
 * 政令指定都市の区(「仙台市　青葉区」)は市の名前にまとめる。東京23区は区の名前のまま。
 */
class MuniTable private constructor(private val entries: Map<Int, Entry>) {
    data class Entry(val prefecture: String, val name: String)

    val size: Int get() = entries.size

    /** 市区町村コード(5桁。先頭2桁が都道府県)から、いる場所を求める。表に無いコードは、県だけ分かる場所として返す */
    fun regionOf(code: Int): Region {
        val prefCode = code / 1000
        val entry = entries[code]
        val prefecture = entry?.prefecture ?: PREFECTURES.getOrNull(prefCode - 1) ?: "不明"
        return Region(prefCode, prefecture, entry?.name?.substringBefore('　')?.trim()?.takeIf { it.isNotEmpty() })
    }

    companion object {
        fun parse(csv: String): MuniTable {
            val map = HashMap<Int, Entry>()
            for (line in csv.lineSequence()) {
                val parts = line.split(',')
                if (parts.size < 3) continue
                val code = parts[0].trim().toIntOrNull() ?: continue
                map[code] = Entry(parts[1].trim(), parts[2].trim())
            }
            return MuniTable(map)
        }

        val PREFECTURES = listOf(
            "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県", "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県",
            "東京都", "神奈川県", "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県", "三重県",
            "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県", "鳥取県", "島根県", "岡山県", "広島県", "山口県", "徳島県",
            "香川県", "愛媛県", "高知県", "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県",
        )
    }
}

/**
 * 都道府県・市区町村をまたいだことを見つける。
 * 位置の誤差や境界の近くで行ったり来たりしても、何度も言わないよう、新しい地域が続けて [confirmCount] 回見えたときだけ、またいだとする。
 * 最初に分かった地域では何も言わない(いま入ったわけではないため)。
 */
class RegionTracker(private val confirmCount: Int = 2) {
    var current: Region? = null
        private set
    private var candidate: Region? = null
    private var candidateCount = 0

    /** 違う地域が見えている(確かめ中)。次の確認を早めるための目印 */
    val checking: Boolean get() = candidate != null

    /** 新しい地域に入ったと確かめられたら、その案内の文を返す */
    fun onRegion(r: Region): String? {
        val cur = current
        if (cur == null) {
            current = r
            return null
        }
        if (sameArea(cur, r)) {
            candidate = null
            candidateCount = 0
            // 市が分かっていなかった地域は、分かったら埋める(案内はしない)
            if (cur.city == null && r.city != null) current = r
            return null
        }
        if (candidate == r) candidateCount++ else {
            candidate = r
            candidateCount = 1
        }
        if (candidateCount < confirmCount) return null
        current = r
        candidate = null
        candidateCount = 0
        return Phrases.enteredRegion(prefectureChanged = cur.prefCode != r.prefCode, prefecture = r.prefecture, city = r.city)
    }

    private fun sameArea(a: Region, b: Region): Boolean = a.prefCode == b.prefCode && (a.city == null || b.city == null || a.city == b.city)
}
