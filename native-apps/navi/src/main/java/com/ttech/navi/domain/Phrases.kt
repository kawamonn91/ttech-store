package com.ttech.navi.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** 音声・画面に出す日本語の言い回し。どれも純粋な関数で、テストで文言を固定している */
object Phrases {
    val JST: ZoneId = ZoneId.of("Asia/Tokyo")

    /**
     * 案内で言う距離。近いほど細かく言う(100m未満は10m刻み、1km未満は50m刻み、10km未満は500m刻み、それ以上は1km刻み)。
     * 例: 50m → 「50メートル」、320m → 「300メートル」、1.2km → 「1キロ」、1.6km → 「1.5キロ」
     */
    fun distance(m: Double): String {
        val v = m.coerceAtLeast(0.0)
        val rounded = when {
            v < 100 -> ((v / 10).roundToInt() * 10).coerceAtLeast(10)
            v < 1000 -> (v / 50).roundToInt() * 50
            v < 10_000 -> (v / 500).roundToInt() * 500
            else -> (v / 1000).roundToInt() * 1000
        }
        return if (rounded < 1000) "${rounded}メートル" else kilo(rounded / 1000.0)
    }

    /** 「1キロ」「1.5キロ」「12キロ」(小数は .5 のときだけ) */
    private fun kilo(km: Double): String {
        val whole = km.toInt()
        return if (abs(km - whole) < 0.01) "${whole}キロ" else "${"%.1f".format(java.util.Locale.US, km)}キロ"
    }

    /** ルート全体・走行距離などの言い方。1km未満はメートル、それ以上は小数1桁のキロ(例: 「154.8キロ」) */
    fun distanceExact(m: Double): String {
        val v = m.coerceAtLeast(0.0)
        return if (v < 1000) "${((v / 10).roundToInt() * 10).coerceAtLeast(10)}メートル" else "${"%.1f".format(java.util.Locale.US, v / 1000)}キロ"
    }

    /** 「2時間5分」「45分」「1分未満」 */
    fun duration(seconds: Double): String {
        val totalMin = (seconds / 60.0).roundToInt()
        if (totalMin < 1) return "1分未満"
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0 -> "${m}分"
            m == 0 -> "${h}時間"
            else -> "${h}時間${m}分"
        }
    }

    /** 到着予定など。5分刻みに丸める(例: 「10時35分」、「11時ちょうど」) */
    fun clock(epochMs: Long): String {
        val t = Instant.ofEpochMilli(roundToMinutes(epochMs, 5)).atZone(JST)
        return if (t.minute == 0) "${t.hour}時ちょうど" else "${t.hour}時${t.minute}分"
    }

    /** 天気の時刻など。10分刻みに丸めて「ごろ」を付ける(例: 「9時半ごろ」、「10時ごろ」、「9時40分ごろ」) */
    fun clockAround(epochMs: Long): String {
        val t = Instant.ofEpochMilli(roundToMinutes(epochMs, 10)).atZone(JST)
        return when (t.minute) {
            0 -> "${t.hour}時ごろ"
            30 -> "${t.hour}時半ごろ"
            else -> "${t.hour}時${t.minute}分ごろ"
        }
    }

    private fun roundToMinutes(epochMs: Long, unit: Int): Long {
        val step = unit * 60_000L
        return Math.floorDiv(epochMs + step / 2, step) * step
    }

    /** 向きの言い方。曲がる向き(左・右)と、方向(斜め・分岐)のときで使い分ける */
    private fun direction(modifier: String?): String = when (modifier) {
        "left", "sharp left", "slight left" -> "左方向"
        "right", "sharp right", "slight right" -> "右方向"
        "straight" -> "直進"
        "uturn" -> "Uターン"
        else -> ""
    }

    /** 動作の名前(「です」を付けて文にする)。例: 「右折」「斜め左方向」「分岐、右方向」「目的地」 */
    fun maneuver(m: Maneuver): String = when (m.kind) {
        ManeuverKind.Turn -> when (m.modifier) {
            "left" -> "左折"
            "right" -> "右折"
            "sharp left" -> "急な左折"
            "sharp right" -> "急な右折"
            "slight left" -> "斜め左方向"
            "slight right" -> "斜め右方向"
            "uturn" -> "Uターン"
            else -> "曲がります"
        }
        ManeuverKind.Fork -> listOf("分岐", direction(m.modifier)).filter { it.isNotEmpty() }.joinToString("、")
        ManeuverKind.OnRamp -> listOf("入口", direction(m.modifier)).filter { it.isNotEmpty() }.joinToString("、")
        ManeuverKind.OffRamp -> listOf("出口", direction(m.modifier)).filter { it.isNotEmpty() }.joinToString("、")
        ManeuverKind.Roundabout -> listOf("環状交差点", direction(m.modifier)).filter { it.isNotEmpty() }.joinToString("、")
        ManeuverKind.Arrive -> "目的地"
    }

    /** 画面の大きな表示用の短い言い方(例: 「右折」「目的地」) */
    fun maneuverShort(m: Maneuver): String = maneuver(m)

    /**
     * 案内の一文。[distanceM] だけ先に動作がある。例: 「200メートル先、右折です。」
     * [near] のときは「まもなく、右折です。」
     */
    fun announce(distanceM: Double, m: Maneuver, near: Boolean): String =
        if (near) "まもなく、${maneuver(m)}です。" else "${distance(distanceM)}先、${maneuver(m)}です。"

    /** もう一つ先の案内。[gapM] は、いまの動作からその動作までの距離。例: 「その後、300メートル先、左折です。」 */
    fun then(gapM: Double, m: Maneuver): String =
        if (gapM < 100) "その後すぐ、${maneuver(m)}です。" else "その後、${distance(gapM)}先、${maneuver(m)}です。"

    /** ナビを始めるときの案内(距離・所要時間・到着予定) */
    fun start(route: Route, departMs: Long): String =
        "ナビを開始します。目的地まで、${distanceExact(route.distanceM)}、約${duration(route.durationS)}です。" +
            "${clock(departMs + (route.durationS * 1000).toLong())}ごろに到着の予定です。"

    /** 到着したときの案内(走った距離・かかった時間) */
    fun arrival(distanceM: Double, durationMs: Long): String =
        "目的地に到着しました。走行距離は${distanceExact(distanceM)}、所要時間は${duration(durationMs / 1000.0)}でした。おつかれさまでした。"

    /** 県・市に入ったときの案内 */
    fun enteredRegion(prefectureChanged: Boolean, prefecture: String, city: String?): String = when {
        prefectureChanged && city != null -> "${prefecture}、${city}に入りました。"
        prefectureChanged -> "${prefecture}に入りました。"
        else -> "${city}に入りました。"
    }
}
