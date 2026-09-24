package com.ttech.track.domain

import java.util.Locale

/**
 * 記録した点を、1行1点のテキストで保存する形式。追記するだけで書けるので、記録の途中でアプリが止まっても
 * それまでの分が残る(壊れた行があっても、その行だけ読み飛ばす)。
 *
 * 列: 時刻(エポックms), 緯度, 経度, 標高, 速度(m/s), 方位, 水平精度, 垂直精度, 速度精度, 衛星数
 * 値が無い項目は空にする。位置は小数7桁(約1cm)で、端末が返した精度を落とさない。
 */
object TrackCodec {
    const val COLUMNS = 10

    fun encode(p: TrackPoint): String = buildString {
        append(p.timeMs)
        append(',').append(fmt(p.lat, 7))
        append(',').append(fmt(p.lon, 7))
        append(',').append(p.altitude?.let { fmt(it, 2) } ?: "")
        append(',').append(p.speed?.let { fmt(it, 3) } ?: "")
        append(',').append(p.bearing?.let { fmt(it, 1) } ?: "")
        append(',').append(p.hAcc?.let { fmt(it, 1) } ?: "")
        append(',').append(p.vAcc?.let { fmt(it, 1) } ?: "")
        append(',').append(p.sAcc?.let { fmt(it, 2) } ?: "")
        append(',').append(p.satellites?.toString() ?: "")
    }

    /** 読めない行(空行・列が足りない・数値でない)は null */
    fun decode(line: String): TrackPoint? {
        val parts = line.trim().split(',')
        if (parts.size < COLUMNS) return null
        val time = parts[0].toLongOrNull() ?: return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val lon = parts[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return TrackPoint(
            timeMs = time,
            lat = lat,
            lon = lon,
            altitude = parts[3].toDoubleOrNull(),
            speed = parts[4].toDoubleOrNull(),
            bearing = parts[5].toDoubleOrNull(),
            hAcc = parts[6].toDoubleOrNull(),
            vAcc = parts[7].toDoubleOrNull(),
            sAcc = parts[8].toDoubleOrNull(),
            satellites = parts[9].toIntOrNull(),
        )
    }

    fun decodeAll(lines: Sequence<String>): List<TrackPoint> = lines.mapNotNull(::decode).toList()

    /** ロケールに左右されず、常に小数点は「.」にする */
    private fun fmt(v: Double, digits: Int): String = String.format(Locale.ROOT, "%.${digits}f", v)
}
