package com.ttech.bikenavi.data

import com.ttech.bikenavi.domain.BRouterParser
import com.ttech.bikenavi.domain.BikeException
import com.ttech.bikenavi.domain.BikeJson
import com.ttech.bikenavi.domain.HourlyForecast
import com.ttech.bikenavi.domain.OpenMeteoParser
import com.ttech.bikenavi.domain.Place
import com.ttech.bikenavi.domain.Route
import com.ttech.bikenavi.domain.RouteStyle
import com.ttech.track.domain.GeoMath
import com.ttech.track.domain.LatLon
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
private fun num(v: Double): String = String.format(Locale.US, "%.6f", v)

/** 経路検索(BRouterの公開サーバー。軽い利用向けなので、ルートを引くとき・引き直すときだけ呼ぶ) */
class BRouterClient(private val http: BikeHttp, private val baseUrl: String = "https://brouter.de/brouter") {
    suspend fun route(from: LatLon, to: LatLon, style: RouteStyle): Route {
        val lonlats = "${num(from.lon)},${num(from.lat)}%7C${num(to.lon)},${num(to.lat)}"
        val url = "$baseUrl?lonlats=$lonlats&profile=${style.profile}&alternativeidx=0&format=geojson"
        val r = http.get(url)
        // ルート無しは400とプレーンテキストの本文で返ってくるので、本文をそのままパーサーに渡して文言にする
        if (!r.ok && r.body.isBlank()) throw BikeException("ルートを取得できませんでした(${r.status})")
        return withContext(Dispatchers.Default) { BRouterParser.parse(r.body) }
    }
}

/** 天気予報(Open-Meteo。キー不要)。地点をまとめて1回で頼む */
class OpenMeteoClient(private val http: BikeHttp, private val baseUrl: String = "https://api.open-meteo.com") {
    suspend fun forecast(points: List<LatLon>): List<HourlyForecast> {
        if (points.isEmpty()) return emptyList()
        val lat = points.joinToString(",") { String.format(Locale.US, "%.4f", it.lat) }
        val lon = points.joinToString(",") { String.format(Locale.US, "%.4f", it.lon) }
        val url = "$baseUrl/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=weather_code,precipitation_probability,precipitation,temperature_2m&timezone=Asia%2FTokyo&forecast_days=3"
        val text = http.getOk(url, "天気予報")
        val list = withContext(Dispatchers.Default) { OpenMeteoParser.parse(text) }
        if (list.size != points.size) throw BikeException("天気予報の地点の数が合いませんでした")
        return list
    }
}

/** 補給・休憩スポットの種類 */
enum class StopKind(val label: String) {
    Convenience("コンビニ"),
    Restaurant("飲食店"),
    RoadsideStation("道の駅"),
}

/** 補給・休憩スポットの候補(Overpass・OpenStreetMapのデータ) */
data class SupplyStop(val kind: StopKind, val place: Place, val distanceFromRouteM: Double)

/** 補給・休憩スポットの検索(Overpass API。OpenStreetMapのデータを検索できる、鍵不要の公開サーバー) */
class OverpassClient(private val http: BikeHttp, private val baseUrl: String = "https://overpass-api.de/api/interpreter") {
    /** [center] の周り [radiusM] 以内にある候補を、近い順に返す */
    suspend fun nearby(center: LatLon, radiusM: Double): List<SupplyStop> {
        val query = """
            [out:json][timeout:15];
            (
              node["shop"="convenience"](around:${radiusM.toInt()},${num(center.lat)},${num(center.lon)});
              node["amenity"="restaurant"](around:${radiusM.toInt()},${num(center.lat)},${num(center.lon)});
              node["amenity"="fast_food"](around:${radiusM.toInt()},${num(center.lat)},${num(center.lon)});
              node["highway"="services"](around:${radiusM.toInt()},${num(center.lat)},${num(center.lon)});
              node["tourism"="information"]["information"="office"](around:${radiusM.toInt()},${num(center.lat)},${num(center.lon)});
            );
            out body;
        """.trimIndent()
        val url = "$baseUrl?data=${enc(query)}"
        val text = http.getOk(url, "補給スポットの検索結果")
        return withContext(Dispatchers.Default) { parse(text, center) }
    }

    companion object {
        fun parse(text: String, center: LatLon): List<SupplyStop> {
            val elements = runCatching { BikeJson.parseToJsonElement(text).jsonObject["elements"]?.jsonArray }.getOrNull() ?: return emptyList()
            return elements.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val tags = o["tags"]?.jsonObject
                val kind = when {
                    tags?.get("shop")?.jsonPrimitive?.contentOrNull == "convenience" -> StopKind.Convenience
                    tags?.get("highway")?.jsonPrimitive?.contentOrNull == "services" -> StopKind.RoadsideStation
                    tags?.get("amenity")?.jsonPrimitive?.contentOrNull in setOf("restaurant", "fast_food") -> StopKind.Restaurant
                    tags?.get("tourism")?.jsonPrimitive?.contentOrNull == "information" -> StopKind.RoadsideStation
                    else -> return@mapNotNull null
                }
                val name = tags?.get("name")?.jsonPrimitive?.contentOrNull ?: kind.label
                val place = Place(name = name, detail = kind.label, lat = lat, lon = lon)
                SupplyStop(kind, place, GeoMath.distanceMeters(center, place.latLon))
            }.sortedBy { it.distanceFromRouteM }
        }
    }
}

/** 全角・半角・空白・ハイフンの違いを無視して比べるための正規化 */
fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase().filter { !it.isWhitespace() && it != '-' && it != 'ー' && it != '−' }

/** 地名検索(Nominatim。OpenStreetMapのデータ)。利用規約に合わせて、1秒に1回までに間隔をあける */
class NominatimClient(
    private val http: BikeHttp,
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
    private val limiter: RateLimiter = RateLimiter(1_100),
) {
    /**
     * [near] があれば、まず近く(目安60km四方)だけに絞って探し、何も見つからなければ
     * 広い範囲(±2度、絞り込みなし)で探し直す(同じ名前のチェーン店などが、現在地と無関係な
     * 場所で上位に出てしまうのを防ぐ)。
     */
    suspend fun search(query: String, near: LatLon? = null): List<Place> {
        if (near != null) {
            val nearby = searchOnce(query, box(near, NEARBY_DEG, bounded = true))
            if (nearby.isNotEmpty()) return nearby
        }
        return searchOnce(query, near?.let { box(it, WIDE_DEG, bounded = false) }.orEmpty())
    }

    private suspend fun searchOnce(query: String, view: String): List<Place> {
        val url = "$baseUrl/search?q=${enc(query)}&format=jsonv2&countrycodes=jp&accept-language=ja&limit=8$view"
        val text = limiter.run { http.getOk(url, "地名の検索結果") }
        return parse(text)
    }

    private fun box(near: LatLon, deg: Double, bounded: Boolean): String =
        "&viewbox=${num(near.lon - deg)},${num(near.lat + deg)},${num(near.lon + deg)},${num(near.lat - deg)}&bounded=${if (bounded) 1 else 0}"

    companion object {
        private const val NEARBY_DEG = 0.27
        private const val WIDE_DEG = 2.0

        fun parse(text: String): List<Place> {
            val array = runCatching { BikeJson.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val lat = o["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = o["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
                val display = o["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val parts = display.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "日本" && !POSTCODE.matches(it) }
                var name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: parts.firstOrNull() ?: return@mapNotNull null
                val category = o["category"]?.jsonPrimitive?.contentOrNull
                val type = o["type"]?.jsonPrimitive?.contentOrNull
                if ((category == "railway" && type in STATION_TYPES) && !name.endsWith("駅")) name += "駅"
                val rest = parts.drop(1)
                Place(name = name, detail = rest.takeLast(3).reversed().joinToString(""), lat = lat, lon = lon)
            }
        }

        private val POSTCODE = Regex("""\d{3}-\d{4}""")
        private val STATION_TYPES = setOf("station", "halt", "stop", "tram_stop")
    }
}

/** 目的地の検索。地名検索(Nominatim)だけを使う(住所・駅名・施設名で探せる) */
class PlaceSearch(private val nominatim: NominatimClient) {
    suspend fun search(query: String, near: LatLon?): List<Place> = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val jobs = buildList {
            add(async { nominatim.search(q, near) })
            if (q.endsWith("駅") && q.length > 1 && !q.contains(' ')) add(async { runCatching { nominatim.search(q.dropLast(1) + " 駅", near) }.getOrDefault(emptyList()) })
        }
        merge(q, jobs.map { it.await() }.flatten(), near)
    }

    companion object {
        /** 近い場所の重複を除き、入力に近い名前・駅・近い場所を先にする */
        fun merge(query: String, places: List<Place>, near: LatLon?, limit: Int = 10): List<Place> {
            val q = normalize(query)
            val unique = ArrayList<Place>()
            for (p in places) {
                if (unique.none { normalize(it.name) == normalize(p.name) && GeoMath.distanceMeters(it.latLon, p.latLon) < 300 }) unique.add(p)
            }
            fun score(p: Place): Double {
                val n = normalize(p.name)
                var s = 0.0
                if (n == q) s -= 3.0 else if (n.startsWith(q)) s -= 1.5 else if (n.contains(q)) s -= 0.5
                if (q.endsWith("駅") && n.endsWith("駅")) s -= 1.0
                if (near != null) s += (GeoMath.distanceMeters(near, p.latLon) / 1000.0).coerceAtMost(600.0) / 200.0
                return s
            }
            return unique.sortedBy(::score).take(limit)
        }
    }
}
