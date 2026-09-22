package com.ttech.dailyfortune.domain

enum class Fortune(val label: String, val message: String) {
    DAIKICHI("大吉", "今日は何をやってもうまくいく日。積極的に動いてみましょう。"),
    KICHI("吉", "落ち着いて過ごせば良いことがありそうです。"),
    CHUKICHI("中吉", "小さな幸運がありそう。周りへの感謝を忘れずに。"),
    SHOKICHI("小吉", "焦らずマイペースに。無理をしないのが吉。"),
    SUEKICHI("末吉", "地道な努力が後で実を結びます。"),
    KYO("凶", "今日は無理をせず、体調管理を優先しましょう。"),
}

/** Webアプリ版と同じ文字列ハッシュ(32bit符号なし相当)。 */
fun hashString(s: String): Long {
    var h = 0L
    for (c in s) {
        h = (h * 31 + c.code) and 0xFFFFFFFFL
    }
    return h
}

/** 日付から今日の運勢を決める。同じ日付なら常に同じ結果になる(サーバー等の乱数に頼らない)。 */
fun fortuneForDate(dateIso: String): Fortune {
    val values = Fortune.entries
    val index = (hashString(dateIso) % values.size).toInt()
    return values[index]
}
