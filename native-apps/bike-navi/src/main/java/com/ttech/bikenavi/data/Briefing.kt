package com.ttech.bikenavi.data

import com.ttech.bikenavi.domain.Route
import com.ttech.bikenavi.domain.SupplyPlanner
import com.ttech.bikenavi.domain.WeatherPlan
import com.ttech.bikenavi.domain.WeatherPlanner

/**
 * ナビを始めるときに読み上げる、天気・補給スポットの案内を作る。
 * 天気は、ペース(ルートの所要時間から求めた平均の速さ)に応じた間隔で、通る時刻に合わせて調べる。
 */
class BriefingService(
    private val meteo: OpenMeteoClient,
    private val overpass: OverpassClient,
) {
    /** 地点ごとの天気予報を調べる([WeatherAlertEngine]で、案内中の声かけにも使う)。予報が取れなければ例外 */
    suspend fun weatherPlan(route: Route, departMs: Long, intervalHours: Double = 1.0): WeatherPlan {
        val samples = WeatherPlanner.samplePoints(route, departMs, intervalHours = intervalHours)
        val forecasts = meteo.forecast(samples.map { it.location })
        val weathers = samples.mapIndexed { i, s -> forecasts[i].at(s.etaMs) }
        return WeatherPlan(samples, weathers)
    }

    /** 予報が取れなければ例外 */
    suspend fun weatherBriefing(route: Route, departMs: Long, intervalHours: Double = 1.0): List<String> {
        val plan = weatherPlan(route, departMs, intervalHours)
        return WeatherPlanner.briefing(plan.samples, plan.weathers, plan.samples.map { null })
    }

    /** 補給・休憩スポットの提案。見つからない地点は、黙って飛ばす(通信できなくても、ナビ自体は続けられるようにする) */
    suspend fun supplyBriefing(route: Route, departMs: Long, restIntervalHours: Double, radiusM: Double = 800.0): List<SupplyStop> {
        val points = SupplyPlanner.restPoints(route, departMs, restIntervalHours)
        return points.mapNotNull { p -> runCatching { overpass.nearby(p.location, radiusM).firstOrNull() }.getOrNull() }
    }
}
