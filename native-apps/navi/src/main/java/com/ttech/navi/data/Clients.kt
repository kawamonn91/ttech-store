package com.ttech.navi.data

import com.ttech.navi.domain.HourlyForecast
import com.ttech.navi.domain.NaviException
import com.ttech.navi.domain.NaviJson
import com.ttech.navi.domain.OpenMeteoParser
import com.ttech.navi.domain.OsrmParser
import com.ttech.navi.domain.Place
import com.ttech.navi.domain.Route
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

/** 経路検索(OSRM の公開サーバー。軽い利用向けなので、ルートを引くとき・引き直すときだけ呼ぶ) */
class OsrmClient(private val http: NaviHttp, private val baseUrl: String = "https://router.project-osrm.org") {
    suspend fun route(from: LatLon, to: LatLon): Route {
        val url = "$baseUrl/route/v1/driving/${num(from.lon)},${num(from.lat)};${num(to.lon)},${num(to.lat)}?overview=full&geometries=geojson&steps=true"
        val r = http.get(url)
        // ルート無し(NoRoute など)は 400 と本文で返ってくるので、本文を解釈して分かりやすい文言にする
        if (!r.ok && r.body.isBlank()) throw NaviException("ルートを取得できませんでした(${r.status})")
        return withContext(Dispatchers.Default) { OsrmParser.parse(r.body) }
    }
}

/** 天気予報(Open-Meteo。キー不要)。地点をまとめて1回で頼む */
class OpenMeteoClient(private val http: NaviHttp, private val baseUrl: String = "https://api.open-meteo.com") {
    suspend fun forecast(points: List<LatLon>): List<HourlyForecast> {
        if (points.isEmpty()) return emptyList()
        val lat = points.joinToString(",") { String.format(Locale.US, "%.4f", it.lat) }
        val lon = points.joinToString(",") { String.format(Locale.US, "%.4f", it.lon) }
        val url = "$baseUrl/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=weather_code,precipitation_probability,precipitation,temperature_2m&timezone=Asia%2FTokyo&forecast_days=2"
        val text = http.getOk(url, "天気予報")
        val list = withContext(Dispatchers.Default) { OpenMeteoParser.parse(text) }
        if (list.size != points.size) throw NaviException("天気予報の地点の数が合いませんでした")
        return list
    }
}

/** 国土地理院の逆ジオコーダーの結果。[muniCd] は5桁の市区町村コード、[town] は町・大字の名前(取れなければ空) */
data class ReverseResult(val muniCd: Int, val town: String)

/** 国土地理院: いまいる場所の市区町村(逆ジオコーディング)と、住所・地名の検索 */
class GsiClient(
    private val http: NaviHttp,
    private val reverseBase: String = "https://mreversegeocoder.gsi.go.jp",
    private val searchBase: String = "https://msearch.gsi.go.jp",
) {
    suspend fun reverse(lat: Double, lon: Double): ReverseResult? {
        val text = http.getOk("$reverseBase/reverse-geocoder/LonLatToAddress?lat=${num(lat)}&lon=${num(lon)}", "現在地の市区町村")
        return parseReverse(text)
    }

    suspend fun search(query: String): List<Place> {
        val text = http.getOk("$searchBase/address-search/AddressSearch?q=${enc(query)}", "住所の検索結果")
        return parseSearch(text, query)
    }

    companion object {
        fun parseReverse(text: String): ReverseResult? {
            val results = runCatching { NaviJson.parseToJsonElement(text).jsonObject["results"] as? JsonObject }.getOrNull() ?: return null
            val code = results["muniCd"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
            val town = results["lv01Nm"]?.jsonPrimitive?.contentOrNull.orEmpty().replace("大字", "").removePrefix("字").trim().takeIf { it != "-" }.orEmpty()
            return ReverseResult(code, town)
        }

        /**
         * 住所・地名の検索結果。部分一致でまったく関係のないものが混じるので、入力した文字を含むもの
         * (住所のように数字を含む入力のときは、そのまま)だけを残す。
         */
        fun parseSearch(text: String, query: String): List<Place> {
            val array = runCatching { NaviJson.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
            val q = normalize(query)
            val looksLikeAddress = query.any { it.isDigit() || it in '０'..'９' } || query.contains("丁目")
            return array.mapNotNull { f ->
                val obj = f as? JsonObject ?: return@mapNotNull null
                val title = obj["properties"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val c = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
                val lon = c[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                val lat = c[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                if (!looksLikeAddress && !normalize(title).contains(q)) return@mapNotNull null
                Place(name = title, detail = "国土地理院", lat = lat, lon = lon)
            }
        }
    }
}

/** 全角・半角・空白・ハイフンの違いを無視して比べるための正規化 */
fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase().filter { !it.isWhitespace() && it != '-' && it != 'ー' && it != '−' }

/** 地名検索(Nominatim。OpenStreetMap のデータ)。利用規約に合わせて、1秒に1回までに間隔をあける */
class NominatimClient(
    private val http: NaviHttp,
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
    private val limiter: RateLimiter = RateLimiter(1_100),
) {
    /** [near] があれば、その周辺(緯度経度で±2度)を優先して探す(絞り込みはしない) */
    suspend fun search(query: String, near: LatLon? = null): List<Place> {
        val view = near?.let { "&viewbox=${num(it.lon - 2)},${num(it.lat + 2)},${num(it.lon + 2)},${num(it.lat - 2)}&bounded=0" }.orEmpty()
        val url = "$baseUrl/search?q=${enc(query)}&format=jsonv2&countrycodes=jp&accept-language=ja&limit=8$view"
        val text = limiter.run { http.getOk(url, "地名の検索結果") }
        return parse(text)
    }

    companion object {
        fun parse(text: String): List<Place> {
            val array = runCatching { NaviJson.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val lat = o["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = o["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
                val display = o["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val parts = display.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "日本" && !POSTCODE.matches(it) }
                var name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: parts.firstOrNull() ?: return@mapNotNull null
                val category = o["category"]?.jsonPrimitive?.contentOrNull
                val type = o["type"]?.jsonPrimitive?.contentOrNull
                // OpenStreetMap では駅の名前に「駅」が付かない(「仙台」など)ので、付けて分かりやすくする
                if ((category == "railway" && type in STATION_TYPES) && !name.endsWith("駅")) name += "駅"
                val rest = parts.drop(1)
                Place(name = name, detail = rest.takeLast(3).reversed().joinToString(""), lat = lat, lon = lon)
            }
        }

        private val POSTCODE = Regex("""\d{3}-\d{4}""")
        private val STATION_TYPES = setOf("station", "halt", "stop", "tram_stop")
    }
}

/**
 * 目的地の検索。地名検索と住所検索を並べて行い、まとめて重ねる。
 * 「仙台駅」のように駅の名前で探すときは、OpenStreetMap の呼び方(「仙台 駅」)でも探す。
 */
class PlaceSearch(private val nominatim: NominatimClient, private val gsi: GsiClient) {
    suspend fun search(query: String, near: LatLon?): List<Place> = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val gsiJob = async { runCatching { gsi.search(q) }.getOrDefault(emptyList()) }
        val osmJobs = buildList {
            add(async { runCatching { nominatim.search(q, near) } })
            if (q.endsWith("駅") && q.length > 1 && !q.contains(' ')) add(async { runCatching { nominatim.search(q.dropLast(1) + " 駅", near) } })
        }
        val osmResults = osmJobs.map { it.await() }
        // 通信できなかったのが、地名検索のすべてなら、その理由を伝える(住所検索の結果だけでも、あれば見せる)
        val gsiPlaces = gsiJob.await()
        val osmPlaces = osmResults.flatMap { it.getOrDefault(emptyList()) }
        if (osmPlaces.isEmpty() && gsiPlaces.isEmpty()) osmResults.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        merge(q, osmPlaces + gsiPlaces, near)
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
