package com.ttech.babylog.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

@Serializable
enum class EventType(val label: String) {
    FEED("授乳"),
    DIAPER("オムツ"),
}

@Serializable
data class BabyEvent(
    val id: String,
    val type: EventType,
    /** 記録した時刻(epoch ms)。 */
    val at: Long,
)

/** 「9/22 07:05」のような表示用の時刻文字列を作る。 */
fun formatEventTime(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val dt = Instant.ofEpochMilli(atMillis).atZone(zone)
    return "%d/%d %02d:%02d".format(dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
}

/**
 * 記録時刻から経過した分数。
 * 時計のずれ等で記録時刻が未来になっていても、表示上は負の値にはしない。
 */
fun minutesAgo(atMillis: Long, nowMillis: Long): Long =
    ((nowMillis - atMillis) / 60_000).coerceAtLeast(0)

/** 種別ごとの直近の記録(先頭が最新である前提)。無ければ null。 */
fun List<BabyEvent>.lastOf(type: EventType): BabyEvent? = firstOrNull { it.type == type }
