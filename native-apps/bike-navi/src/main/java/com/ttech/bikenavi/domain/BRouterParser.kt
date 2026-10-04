package com.ttech.bikenavi.domain

import com.ttech.track.domain.LatLon
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** BRouter(自転車・徒歩向けの経路検索。鍵不要の公開サーバー)の応答(GeoJSON)を [Route] にする */
object BRouterParser {
    /** 標高の変化が、これ未満(m)なら「平ら」とみなし、登り・下りの合計には数えない(標高データの細かい揺れを無視するため) */
    private const val ELEVATION_NOISE_M = 1.0

    fun parse(text: String): Route {
        val root = try {
            BikeJson.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BikeException(errorMessage(text), e)
        }
        val feature = root["features"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw BikeException(errorMessage(text))
        val props = feature["properties"]?.jsonObject ?: throw BikeException("ルートの情報を読み取れませんでした")
        val coordinates = feature["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: throw BikeException("ルートの形が取得できませんでした")

        // 緯度・経度・標高を、同じ並びで取り出す(重複した点は、標高も合わせて1つに間引く)
        val points = ArrayList<LatLon>(coordinates.size)
        val elevations = ArrayList<Double>(coordinates.size)
        for (c in coordinates) {
            val triple = c.jsonArray
            val lon = triple[0].jsonPrimitive.double()
            val lat = triple[1].jsonPrimitive.double()
            val ele = triple.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: 0.0
            val p = LatLon(lat, lon)
            if (points.lastOrNull() != p) {
                points.add(p)
                elevations.add(ele)
            }
        }
        if (points.size < 2) throw BikeException("ルートが短すぎます")
        val line = Polyline(points)
        val elevationProfile = points.indices.map { ElevationPoint(line.cumM[it], elevations[it]) }

        var ascend = 0.0
        var descend = 0.0
        for (i in 1 until elevations.size) {
            val d = elevations[i] - elevations[i - 1]
            if (abs(d) < ELEVATION_NOISE_M) continue
            if (d > 0) ascend += d else descend += -d
        }

        // 距離は、折れ線そのものの長さを使う(曲がり角の道のりなど、ほかの計算と食い違わないように)
        val durationS = str(props, "total-time")?.toDoubleOrNull() ?: 0.0
        // 登りは、BRouter自身の「ならした」値を使う(標高データの細かい揺れを、こちらの単純なしきい値より上手に除いているため)
        val ascendFromServer = str(props, "filtered ascend")?.toDoubleOrNull()

        val wayPoints = parseWayPoints(props["messages"] as? JsonArray)
        val maneuvers = ManeuverDetector.detect(line) { atM -> wayTagsNear(wayPoints, atM) }

        return Route(line, maneuvers, distanceM = line.lengthM, durationS = durationS, ascendM = ascendFromServer ?: ascend, descendM = descend, elevation = elevationProfile)
    }

    private fun str(obj: kotlinx.serialization.json.JsonObject, key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull

    private fun kotlinx.serialization.json.JsonElement.double(): Double =
        jsonPrimitive.doubleOrNull ?: throw BikeException("ルートの座標を読み取れませんでした")

    private data class WayPoint(val atM: Double, val wayTags: String)

    /** [messages] の各行(先頭はヘッダー)から、出発地からの道のりごとの道のタグ(highway=... など)を作る */
    private fun parseWayPoints(messages: JsonArray?): List<WayPoint> {
        if (messages == null || messages.size < 2) return emptyList()
        var acc = 0.0
        val out = ArrayList<WayPoint>(messages.size - 1)
        for (row in messages.drop(1)) {
            val arr = row.jsonArray
            val dist = arr.getOrNull(3)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
            acc += dist
            val wayTags = arr.getOrNull(9)?.jsonPrimitive?.contentOrNull.orEmpty()
            out.add(WayPoint(acc, wayTags))
        }
        return out
    }

    private fun wayTagsNear(wayPoints: List<WayPoint>, atM: Double): String? {
        if (wayPoints.isEmpty()) return null
        return wayPoints.firstOrNull { it.atM >= atM }?.wayTags ?: wayPoints.lastOrNull()?.wayTags
    }

    /** BRouterはエラーをJSONではなく、プレーンテキストで返すことがある(例: "from-position not mapped in existing datafile") */
    private fun errorMessage(rawText: String): String {
        val t = rawText.trim()
        return when {
            t.contains("not mapped", ignoreCase = true) -> "出発地または目的地の近くに、自転車で通れる道が見つかりませんでした"
            t.isEmpty() -> "ルートが見つかりませんでした"
            t.length < 200 -> "ルートを取得できませんでした($t)"
            else -> "ルートの応答を読み取れませんでした"
        }
    }
}
