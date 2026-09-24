package com.ttech.track.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 他のアプリ(地図アプリ・解析ツール)で使えるように、記録を GPX / CSV で書き出す */
object Export {
    private val iso = DateTimeFormatter.ISO_INSTANT

    fun isoTime(ms: Long): String = iso.format(Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC))

    fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    /** GPX 1.1。速度・方位・精度・衛星数は extensions に入れる */
    fun gpx(name: String, points: List<TrackPoint>, creator: String = "T-tech"): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("<gpx version=\"1.1\" creator=\"").append(xmlEscape(creator)).append("\" xmlns=\"http://www.topografix.com/GPX/1/1\">").append('\n')
        append("  <metadata><name>").append(xmlEscape(name)).append("</name>")
        if (points.isNotEmpty()) append("<time>").append(isoTime(points.first().timeMs)).append("</time>")
        append("</metadata>\n")
        append("  <trk>\n    <name>").append(xmlEscape(name)).append("</name>\n    <trkseg>\n")
        for (p in points) {
            append("      <trkpt lat=\"").append(num(p.lat, 7)).append("\" lon=\"").append(num(p.lon, 7)).append("\">")
            p.altitude?.let { append("<ele>").append(num(it, 2)).append("</ele>") }
            append("<time>").append(isoTime(p.timeMs)).append("</time>")
            val ext = buildString {
                p.speed?.let { append("<speed>").append(num(it, 3)).append("</speed>") }
                p.bearing?.let { append("<course>").append(num(it, 1)).append("</course>") }
                p.hAcc?.let { append("<hacc>").append(num(it, 1)).append("</hacc>") }
                p.satellites?.let { append("<sat>").append(it).append("</sat>") }
            }
            if (ext.isNotEmpty()) append("<extensions>").append(ext).append("</extensions>")
            append("</trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    const val CSV_HEADER = "time_utc,lat,lon,altitude_m,speed_mps,speed_kmh,bearing_deg,h_accuracy_m,v_accuracy_m,speed_accuracy_mps,satellites"

    fun csv(points: List<TrackPoint>): String = buildString {
        append(CSV_HEADER).append('\n')
        for (p in points) {
            append(isoTime(p.timeMs))
            append(',').append(num(p.lat, 7))
            append(',').append(num(p.lon, 7))
            append(',').append(p.altitude?.let { num(it, 2) } ?: "")
            append(',').append(p.speed?.let { num(it, 3) } ?: "")
            append(',').append(p.speed?.let { num(it * 3.6, 2) } ?: "")
            append(',').append(p.bearing?.let { num(it, 1) } ?: "")
            append(',').append(p.hAcc?.let { num(it, 1) } ?: "")
            append(',').append(p.vAcc?.let { num(it, 1) } ?: "")
            append(',').append(p.sAcc?.let { num(it, 2) } ?: "")
            append(',').append(p.satellites?.toString() ?: "")
            append('\n')
        }
    }

    private fun num(v: Double, digits: Int): String = String.format(Locale.ROOT, "%.${digits}f", v)
}
