package com.ttech.navi.domain

import com.ttech.track.domain.LatLon
import kotlin.math.pow
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Valhalla(公開サーバー)の経路検索の応答を [Route] にする。
 * 有料道路を避けるように頼んだ経路だけを扱う。応答の summary.has_highway が false(高速道路・自動車専用道路を使わない)
 * のときだけ、[Route.tollFree] を立てて返す。高速道路を使ってしまった経路は、避けられていないので返さない。
 */
object ValhallaParser {
    private const val PRECISION = 6

    /** 高速道路を使わない経路。使えなかった・応答に問題があるときは null */
    fun parseTollFree(text: String): Route? {
        val root = try {
            NaviJson.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw NaviException("ルートの応答を読み取れませんでした", e)
        }
        val trip = root["trip"]?.jsonObject ?: return null
        val summary = trip["summary"]?.jsonObject ?: return null
        if (summary["has_highway"]?.jsonPrimitive?.booleanOrNull != false) return null

        val leg = trip["legs"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val shape = leg["shape"]?.jsonPrimitive?.contentOrNull ?: return null
        val points = decodePolyline(shape, PRECISION)
        if (points.size < 2) return null
        val line = Polyline(points)

        // 各操作の位置は、折れ線の何番目の点から始まるか(begin_shape_index)で決まる。道のりはその点までの累積距離
        val maneuverJson = leg["maneuvers"]?.jsonArray.orEmpty().map { it.jsonObject }
        val stepStarts = DoubleArray(maneuverJson.size)
        val stepDurations = DoubleArray(maneuverJson.size)
        val maneuvers = ArrayList<Maneuver>()
        for ((i, m) in maneuverJson.withIndex()) {
            val begin = (m["begin_shape_index"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, points.lastIndex)
            stepStarts[i] = line.cumM[begin]
            stepDurations[i] = m["time"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val type = m["type"]?.jsonPrimitive?.intOrNull ?: continue
            val (kind, modifier) = kindOf(type) ?: continue
            val at = if (kind == ManeuverKind.Arrive) line.lengthM else stepStarts[i]
            val road = m["street_names"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty()
            maneuvers.add(Maneuver(kind, modifier, at, road, points[begin]))
        }
        if (maneuvers.lastOrNull()?.kind != ManeuverKind.Arrive) {
            maneuvers.add(Maneuver(ManeuverKind.Arrive, null, line.lengthM, "", points.last()))
        }

        val durationS = summary["time"]?.jsonPrimitive?.doubleOrNull ?: stepDurations.sum()
        return Route(
            line = line,
            maneuvers = maneuvers,
            stepStartsM = stepStarts,
            stepDurationsS = stepDurations,
            distanceM = line.lengthM,
            durationS = durationS,
            tollDistanceM = 0.0,
            tollFree = true,
        )
    }

    /** Valhalla の操作の種類を、案内の種類と向き(OSRM と同じ言い方)に直す。案内しないものは null */
    private fun kindOf(type: Int): Pair<ManeuverKind, String?>? = when (type) {
        4 -> ManeuverKind.Arrive to null
        5 -> ManeuverKind.Arrive to "right"
        6 -> ManeuverKind.Arrive to "left"
        9 -> ManeuverKind.Turn to "slight right"
        10 -> ManeuverKind.Turn to "right"
        11 -> ManeuverKind.Turn to "sharp right"
        12, 13 -> ManeuverKind.Turn to "uturn"
        14 -> ManeuverKind.Turn to "sharp left"
        15 -> ManeuverKind.Turn to "left"
        16 -> ManeuverKind.Turn to "slight left"
        17 -> ManeuverKind.OnRamp to null
        18 -> ManeuverKind.OnRamp to "right"
        19 -> ManeuverKind.OnRamp to "left"
        20 -> ManeuverKind.OffRamp to "right"
        21 -> ManeuverKind.OffRamp to "left"
        22 -> ManeuverKind.Fork to null
        23 -> ManeuverKind.Fork to "right"
        24 -> ManeuverKind.Fork to "left"
        26, 27 -> ManeuverKind.Roundabout to null
        else -> null
    }
}

/**
 * Google の encoded polyline(Valhalla の shape)を、緯度経度の列にする。
 * [precision] は小数点以下の桁数(Valhalla は6)。
 */
internal fun decodePolyline(encoded: String, precision: Int): List<LatLon> {
    val factor = 10.0.pow(precision)
    val out = ArrayList<LatLon>()
    var pos = 0
    var lat = 0
    var lng = 0

    fun next(): Int {
        var result = 0
        var shift = 0
        while (true) {
            if (pos >= encoded.length) throw NaviException("ルートの形を読み取れませんでした")
            val b = encoded[pos++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return if ((result and 1) != 0) (result shr 1).inv() else result shr 1
    }

    while (pos < encoded.length) {
        lat += next()
        lng += next()
        out.add(LatLon(lat / factor, lng / factor))
    }
    return out
}
