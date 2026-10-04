package com.ttech.bikenavi.domain

import com.ttech.track.domain.LatLon
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** ある時刻の天気の予報 */
data class Weather(val timeMs: Long, val code: Int, val popPercent: Int, val precipMm: Double, val tempC: Double?)

/** 1地点の、1時間ごとの予報(Open-Meteo の hourly をそのまま並べたもの) */
class HourlyForecast(
    private val timesMs: LongArray,
    private val codes: IntArray,
    private val pops: IntArray,
    private val precips: DoubleArray,
    private val temps: DoubleArray,
) {
    val size: Int get() = timesMs.size

    /** [epochMs] にいちばん近い時刻の予報。予報の範囲(前後1.5時間)から外れていれば null */
    fun at(epochMs: Long): Weather? {
        if (timesMs.isEmpty()) return null
        var best = 0
        for (i in timesMs.indices) if (abs(timesMs[i] - epochMs) < abs(timesMs[best] - epochMs)) best = i
        if (abs(timesMs[best] - epochMs) > 90 * 60_000L) return null
        return Weather(timesMs[best], codes[best], pops[best], precips[best], temps[best].takeIf { !it.isNaN() })
    }

    companion object {
        val JST: ZoneId = ZoneId.of("Asia/Tokyo")

        /** Open-Meteo の時刻(「2026-09-28T09:00」。日本時間で依頼している)を、エポックミリ秒にする */
        fun parseTime(text: String): Long = LocalDateTime.parse(text).atZone(JST).toInstant().toEpochMilli()

        fun of(times: List<String>, codes: List<Int?>, pops: List<Int?>, precips: List<Double?>, temps: List<Double?>): HourlyForecast =
            HourlyForecast(
                LongArray(times.size) { parseTime(times[it]) },
                IntArray(times.size) { codes.getOrNull(it) ?: 0 },
                IntArray(times.size) { pops.getOrNull(it) ?: 0 },
                DoubleArray(times.size) { precips.getOrNull(it) ?: 0.0 },
                DoubleArray(times.size) { temps.getOrNull(it) ?: Double.NaN },
            )
    }
}

/** 雨・雪などの度合い(大きいほど、道中で気にしたい) */
enum class Precip(val rank: Int, val label: String) {
    None(0, ""),
    RainPossible(1, "雨"),
    Rain(2, "雨"),
    HeavyRain(3, "強い雨"),
    Snow(4, "雪"),
    Thunder(5, "雷雨"),
}

object WeatherCodes {
    /** WMO の天気コード → 言い方 */
    fun label(code: Int): String = when (code) {
        0 -> "快晴"
        1 -> "晴れ"
        2 -> "晴れ時々曇り"
        3 -> "曇り"
        45, 48 -> "霧"
        51, 53, 55 -> "霧雨"
        56, 57 -> "凍る霧雨"
        61, 63 -> "雨"
        65 -> "強い雨"
        66, 67 -> "凍る雨"
        71, 73 -> "雪"
        75 -> "大雪"
        77 -> "霧雪"
        80, 81 -> "にわか雨"
        82 -> "激しいにわか雨"
        85, 86 -> "にわか雪"
        95 -> "雷雨"
        96, 99 -> "雷を伴う雨"
        else -> "不明"
    }

    fun classify(w: Weather): Precip = when {
        w.code in 95..99 -> Precip.Thunder
        w.code in 71..77 || w.code == 85 || w.code == 86 -> Precip.Snow
        w.code == 65 || w.code == 67 || w.code == 82 || w.precipMm >= 4.0 -> Precip.HeavyRain
        w.code in 51..64 || w.code == 66 || w.code == 80 || w.code == 81 || w.precipMm >= 0.5 -> Precip.Rain
        w.popPercent >= 60 -> Precip.RainPossible
        else -> Precip.None
    }
}

/** 天気を調べる地点。[progressM] は出発地からの道のり、[etaMs] はそこを通る予定の時刻 */
data class SamplePoint(val progressM: Double, val location: LatLon, val etaMs: Long, val isDestination: Boolean)

/** 出発前に読み上げる天気の案内を作る。自転車は車よりかなり遅いので、距離ではなく「ペース(速さ)」から間隔を決める */
object WeatherPlanner {
    /**
     * 天気を調べる地点。ルートを、だいたい [intervalHours] 時間走るごとに区切った地点(最大 [maxPoints] 個)。最後は目的地。
     * 通る時刻は、ルートの所要時間の見積もりから求める。
     */
    fun samplePoints(route: Route, departMs: Long, maxPoints: Int = 8, intervalHours: Double = 1.0): List<SamplePoint> {
        val total = route.distanceM
        val avgSpeedMps = if (route.durationS > 0) total / route.durationS else 5.0
        val spacingM = (avgSpeedMps * 3600.0 * intervalHours).coerceAtLeast(5_000.0)
        // 端数で、地点が1つ増えないように、わずかに引く
        val n = ceil(total / spacingM - 0.02).toInt().coerceIn(1, maxPoints)
        return (1..n).map { i ->
            val p = if (i == n) total else total * i / n
            val eta = departMs + (route.timeAtM(p) * 1000).toLong()
            SamplePoint(p, if (i == n) route.destination else route.line.pointAt(p), eta, isDestination = i == n)
        }
    }

    /**
     * 天気の案内の文(読み上げ用)を作る。[weathers] と [places] は [samples] と同じ順。
     * 予報が取れなかった地点は null。[places] は地点の名前(分からなければ null)。
     */
    fun briefing(samples: List<SamplePoint>, weathers: List<Weather?>, places: List<String?>): List<String> {
        if (samples.isEmpty()) return emptyList()
        val lines = ArrayList<String>()

        val destWeather = weathers.lastOrNull()
        val dest = samples.last()
        if (destWeather != null) {
            val temp = destWeather.tempC?.let { "、気温は${it.roundToInt()}度" } ?: ""
            lines.add("目的地の${Phrases.clockAround(dest.etaMs)}の天気は、${WeatherCodes.label(destWeather.code)}${temp}の予報です。")
        } else {
            lines.add("目的地の天気予報は、取得できませんでした。")
        }

        val flagged = samples.indices.filter { i ->
            val w = weathers.getOrNull(i)
            w != null && WeatherCodes.classify(w) != Precip.None
        }
        if (weathers.all { it == null }) return lines

        if (flagged.isEmpty()) {
            lines.add("道中は、雨や雪の心配はなさそうです。")
            return lines
        }
        var start = 0
        val groups = ArrayList<List<Int>>()
        while (start < flagged.size) {
            var end = start
            while (end + 1 < flagged.size && flagged[end + 1] == flagged[end] + 1) end++
            groups.add(flagged.subList(start, end + 1))
            start = end + 1
        }
        for (g in groups.take(2)) {
            val worst = g.mapNotNull { weathers[it] }.map { WeatherCodes.classify(it) }.maxByOrNull { it.rank } ?: continue
            val first = samples[g.first()]
            val last = samples[g.last()]
            val placeA = places.getOrNull(g.first())
            val placeB = places.getOrNull(g.last())
            val where = when {
                g.size == 1 || placeA == null || placeB == null || placeA == placeB -> (placeA ?: placeB)?.let { "${it}付近で" } ?: (if (first.isDestination) "目的地付近で" else "")
                else -> "${placeA}から${placeB}付近にかけて"
            }
            val whenText = if (g.size == 1) "${Phrases.clockAround(first.etaMs)}、" else "${Phrases.clockAround(first.etaMs)}から${Phrases.clockAround(last.etaMs)}にかけて、"
            lines.add("${whenText}${where}${worst.label}の可能性があります。")
        }
        if (groups.size > 2) lines.add("ほかにも、天気が崩れる場所がありそうです。")
        return lines
    }
}
