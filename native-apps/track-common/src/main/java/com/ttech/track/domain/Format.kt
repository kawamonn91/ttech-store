package com.ttech.track.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 画面に出す文字列の整形(日本語) */
object Format {
    private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

    /** 1km未満は m、以上は小数1〜2桁の km */
    fun distance(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToInt()} m"
        meters < 100_000 -> String.format(Locale.ROOT, "%.2f km", meters / 1000)
        else -> String.format(Locale.ROOT, "%.1f km", meters / 1000)
    }

    fun distanceKmNumber(meters: Double): String = String.format(Locale.ROOT, "%.2f", meters / 1000)

    /** 例: 1時間23分 / 5分12秒 / 48秒 */
    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "${h}時間${m}分"
            m > 0 -> "${m}分${s}秒"
            else -> "${s}秒"
        }
    }

    /** 時:分:秒(記録中の経過時間など) */
    fun clock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    /** ペース(1kmあたりの時間)。例: 5:32 /km。速度が遅すぎる(ほぼ停止)ときは「-」 */
    fun pace(secPerKm: Double): String {
        if (!secPerKm.isFinite() || secPerKm <= 0 || secPerKm > 99 * 60) return "-"
        val total = secPerKm.roundToInt()
        return String.format(Locale.ROOT, "%d:%02d /km", total / 60, total % 60)
    }

    fun paceNumber(secPerKm: Double): String = pace(secPerKm).removeSuffix(" /km")

    /** 速度(m/s)からペース(秒/km)。止まっているときは無限大 */
    fun secPerKm(mps: Double): Double = if (mps > 0.05) 1000.0 / mps else Double.POSITIVE_INFINITY

    fun speedKmh(mps: Double): String = "${(mps * 3.6).roundToInt()} km/h"

    fun kmhNumber(mps: Double): Int = (mps * 3.6).roundToInt()

    fun meters(value: Double): String = "${value.roundToInt()} m"

    fun date(ms: Long, zone: ZoneId = JST): String =
        DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPAN).format(Instant.ofEpochMilli(ms).atZone(zone))

    fun dateShort(ms: Long, zone: ZoneId = JST): String =
        DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN).format(Instant.ofEpochMilli(ms).atZone(zone))

    fun time(ms: Long, zone: ZoneId = JST): String =
        DateTimeFormatter.ofPattern("H:mm", Locale.JAPAN).format(Instant.ofEpochMilli(ms).atZone(zone))

    fun timeSeconds(ms: Long, zone: ZoneId = JST): String =
        DateTimeFormatter.ofPattern("H:mm:ss", Locale.JAPAN).format(Instant.ofEpochMilli(ms).atZone(zone))

    fun dateTime(ms: Long, zone: ZoneId = JST): String =
        DateTimeFormatter.ofPattern("yyyy/M/d H:mm", Locale.JAPAN).format(Instant.ofEpochMilli(ms).atZone(zone))

    /** 一覧を日付ごとに分けるためのキー(日本時間の日付) */
    fun dayKey(ms: Long, zone: ZoneId = JST): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    fun coords(lat: Double, lon: Double): String = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)
}
