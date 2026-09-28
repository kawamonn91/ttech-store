package com.ttech.navi.data

import com.ttech.navi.domain.MuniTable
import com.ttech.navi.domain.Route
import com.ttech.navi.domain.WeatherPlanner
import com.ttech.track.domain.LatLon
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * ナビを始めるときに読み上げる、天気の案内を作る。
 * ルートに沿って20kmごとと目的地の予報を、通る時刻に合わせて調べ、雨・雪になりそうな場所を、地名(市区町村と町名)つきで伝える。
 */
class BriefingService(
    private val meteo: OpenMeteoClient,
    private val gsi: GsiClient,
    private val muni: () -> MuniTable,
) {
    /** 予報が取れなければ例外(地名が取れなかった地点は、地名なしで案内する) */
    suspend fun briefing(route: Route, departMs: Long): List<String> = coroutineScope {
        val samples = WeatherPlanner.samplePoints(route, departMs)
        val forecasts = async { meteo.forecast(samples.map { it.location }) }
        val places = samples.map { s -> async { runCatching { placeName(s.location) }.getOrNull() } }
        val list = forecasts.await()
        val weathers = samples.mapIndexed { i, s -> list[i].at(s.etaMs) }
        WeatherPlanner.briefing(samples, weathers, places.map { it.await() })
    }

    private suspend fun placeName(p: LatLon): String? {
        val r = gsi.reverse(p.lat, p.lon) ?: return null
        val region = muni().regionOf(r.muniCd)
        return (region.city.orEmpty() + r.town).ifBlank { null }
    }
}
