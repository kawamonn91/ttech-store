package com.ttech.bikenavi.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** 音声・画面に出す日本語の言い回し。どれも純粋な関数で、テストで文言を固定している */
object Phrases {
    val JST: ZoneId = ZoneId.of("Asia/Tokyo")

    /**
     * 案内で言う距離。近いほど細かく言う(100m未満は10m刻み、1km未満は50m刻み、それ以上は1km刻み)。
     * 自転車は車より遅いので、近い距離を中心にした言い方にする。
     */
    fun distance(m: Double): String {
        val v = m.coerceAtLeast(0.0)
        val rounded = when {
            v < 100 -> ((v / 10).roundToInt() * 10).coerceAtLeast(10)
            v < 1000 -> (v / 50).roundToInt() * 50
            else -> (v / 500).roundToInt() * 500
        }
        return if (rounded < 1000) "${rounded}メートル" else kilo(rounded / 1000.0)
    }

    private fun kilo(km: Double): String {
        val whole = km.toInt()
        return if (abs(km - whole) < 0.01) "${whole}キロ" else "${"%.1f".format(java.util.Locale.US, km)}キロ"
    }

    fun distanceExact(m: Double): String {
        val v = m.coerceAtLeast(0.0)
        return if (v < 1000) "${((v / 10).roundToInt() * 10).coerceAtLeast(10)}メートル" else "${"%.1f".format(java.util.Locale.US, v / 1000)}キロ"
    }

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

    fun clock(epochMs: Long): String {
        val t = Instant.ofEpochMilli(roundToMinutes(epochMs, 5)).atZone(JST)
        return if (t.minute == 0) "${t.hour}時ちょうど" else "${t.hour}時${t.minute}分"
    }

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

    private fun direction(modifier: String?): String = when (modifier) {
        "left", "sharp left", "slight left" -> "左方向"
        "right", "sharp right", "slight right" -> "右方向"
        "uturn" -> "Uターン"
        else -> ""
    }

    /** 動作の名前。例: 「右折」「斜め左方向」「目的地」 */
    fun maneuver(m: Maneuver): String {
        if (m.kind == ManeuverKind.Arrive) return "目的地"
        return when (m.modifier) {
            "left" -> "左折"
            "right" -> "右折"
            "sharp left" -> "急な左折"
            "sharp right" -> "急な右折"
            "slight left" -> "斜め左方向"
            "slight right" -> "斜め右方向"
            "uturn" -> "Uターン"
            else -> listOf("曲がり角", direction(m.modifier)).filter { it.isNotEmpty() }.joinToString("、")
        }
    }

    /** 案内の一文。[near] のときは「まもなく」。二段階右折の目安があるときは、それも添える */
    fun announce(distanceM: Double, m: Maneuver, near: Boolean): String {
        val base = if (near) "まもなく、${maneuver(m)}です。" else "${distance(distanceM)}先、${maneuver(m)}です。"
        return if (m.twoStageRightTurn) "$base 広い道路です。二段階右折の対象になることがあります。" else base
    }

    /** ナビを始めるときの案内(距離・所要時間・獲得標高・到着予定) */
    fun start(route: Route, departMs: Long): String {
        val climb = if (route.ascendM >= 30.0) "獲得標高は約${route.ascendM.roundToInt()}メートルです。" else ""
        return "ナビを開始します。目的地まで、${distanceExact(route.distanceM)}、約${duration(route.durationS)}です。$climb" +
            "${clock(departMs + (route.durationS * 1000).toLong())}ごろに到着の予定です。"
    }

    /** 到着したときの案内(走った距離・かかった時間) */
    fun arrival(distanceM: Double, durationMs: Long): String =
        "目的地に到着しました。走行距離は${distanceExact(distanceM)}、所要時間は${duration(durationMs / 1000.0)}でした。おつかれさまでした。"
}
