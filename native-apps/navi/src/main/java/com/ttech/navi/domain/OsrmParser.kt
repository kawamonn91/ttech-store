package com.ttech.navi.domain

import com.ttech.track.domain.LatLon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** OSRM の経路検索(route サービス、geometries=geojson、steps=true)の応答を [Route] にする */
object OsrmParser {
    /** ルートの折れ線から、この距離(m)以上はなれた場所は、曲がり角の位置として信用せず、道のりの積み上げで求める */
    private const val SNAP_TRUST_M = 40.0

    /** 最初の(OSRMがいちばん良いと判断した)経路だけを取り出す */
    fun parse(text: String): Route = parseAll(text).first()

    /** OSRMが返した経路の候補すべて(alternatives=trueのとき複数)を取り出す */
    fun parseAll(text: String): List<Route> {
        val root = try {
            NaviJson.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw NaviException("ルートの応答を読み取れませんでした", e)
        }
        val code = root["code"]?.jsonPrimitive?.contentOrNull
        if (code != "Ok") throw NaviException(errorMessage(code))
        val routes = root["routes"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        if (routes.isEmpty()) throw NaviException("ルートが見つかりませんでした")
        return routes.map(::parseOne)
    }

    private fun parseOne(route: JsonObject): Route {
        val coordinates = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: throw NaviException("ルートの形が取得できませんでした")
        val points = ArrayList<LatLon>(coordinates.size)
        for (c in coordinates) {
            val pair = c.jsonArray
            val p = LatLon(lat = pair[1].jsonPrimitive.double(), lon = pair[0].jsonPrimitive.double())
            if (points.lastOrNull() != p) points.add(p)
        }
        if (points.size < 2) throw NaviException("ルートが短すぎます")
        val line = Polyline(points)

        val steps = route["legs"]?.jsonArray.orEmpty().flatMap { it.jsonObject["steps"]?.jsonArray.orEmpty() }.map { it.jsonObject }

        // 各ステップの出発地からの道のり(OSRM の距離の積み上げ。折れ線の長さに合わせて按分する)
        val rawLengths = steps.map { it["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0 }
        val rawTotal = rawLengths.sum()
        val scale = if (rawTotal > 1e-6) line.lengthM / rawTotal else 1.0
        val stepStarts = DoubleArray(steps.size)
        var acc = 0.0
        for (i in steps.indices) {
            stepStarts[i] = acc
            acc += rawLengths[i] * scale
        }
        val durations = DoubleArray(steps.size) { steps[it]["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0 }

        // 高速道路(の目安)を通る区間の距離を合計する(道のりは、折れ線の長さに合わせた按分後の値で数える)
        var tollDistanceM = 0.0
        for (i in steps.indices) {
            val ref = steps[i]["ref"]?.jsonPrimitive?.contentOrNull
            val name = steps[i]["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (isTollRoad(ref, name)) tollDistanceM += rawLengths[i] * scale
        }

        val maneuvers = ArrayList<Maneuver>()
        var searchFrom = 0
        for ((i, step) in steps.withIndex()) {
            val m = step["maneuver"]?.jsonObject ?: continue
            val type = m["type"]?.jsonPrimitive?.contentOrNull ?: continue
            val modifier = m["modifier"]?.jsonPrimitive?.contentOrNull
            val kind = kindOf(type, modifier) ?: continue
            val loc = m["location"]?.jsonArray?.let { LatLon(lat = it[1].jsonPrimitive.double(), lon = it[0].jsonPrimitive.double()) } ?: continue

            val atM = if (kind == ManeuverKind.Arrive) {
                line.lengthM
            } else {
                val proj = line.project(loc, from = searchFrom.coerceAtMost(line.segmentCount - 1))
                if (proj.distM <= SNAP_TRUST_M) {
                    searchFrom = proj.seg
                    proj.progressM
                } else {
                    stepStarts[i]
                }
            }
            val ordered = maxOf(atM, maneuvers.lastOrNull()?.atM ?: 0.0)
            maneuvers.add(Maneuver(kind, modifier, ordered, step["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), loc))
        }
        // 到着(arrive)のステップが無い応答でも、最後に目的地を入れておく
        if (maneuvers.lastOrNull()?.kind != ManeuverKind.Arrive) {
            maneuvers.add(Maneuver(ManeuverKind.Arrive, null, line.lengthM, "", points.last()))
        }

        val durationS = route["duration"]?.jsonPrimitive?.doubleOrNull ?: durations.sum()
        return Route(line, maneuvers, stepStarts, durations, distanceM = line.lengthM, durationS = durationS, tollDistanceM = tollDistanceM)
    }

    private fun JsonElement.double(): Double = jsonPrimitive.doubleOrNull ?: throw NaviException("ルートの座標を読み取れませんでした")


    /** 案内する動作にあたるものだけを選ぶ。道なりに進むだけの指示(new name など)・合流は案内しない */
    fun kindOf(type: String, modifier: String?): ManeuverKind? = when (type) {
        "turn", "end of road" -> if (modifier == null || modifier == "straight") null else ManeuverKind.Turn
        "continue" -> if (modifier in TURNING_MODIFIERS) ManeuverKind.Turn else null
        "fork" -> ManeuverKind.Fork
        "on ramp" -> ManeuverKind.OnRamp
        "off ramp" -> ManeuverKind.OffRamp
        "roundabout", "rotary", "roundabout turn" -> ManeuverKind.Roundabout
        "arrive" -> ManeuverKind.Arrive
        else -> null
    }

    private val TURNING_MODIFIERS = setOf("left", "right", "sharp left", "sharp right", "uturn")

    private fun errorMessage(code: String?): String = when (code) {
        "NoRoute" -> "ルートが見つかりませんでした(道路でつながっていない可能性があります)"
        "NoSegment" -> "出発地または目的地の近くに、道路が見つかりませんでした"
        "TooBig" -> "距離が長すぎて、ルートを検索できませんでした"
        else -> "ルートを取得できませんでした(${code ?: "不明"})"
    }
}
