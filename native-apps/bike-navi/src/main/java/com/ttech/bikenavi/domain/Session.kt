package com.ttech.bikenavi.domain

import com.ttech.track.domain.GeoMath
import kotlin.math.max

/** 利用者が変えられる設定 */
data class BikeSettings(
    val voice: Boolean = true,
    val weatherBriefing: Boolean = true,
    val speechRate: Float = 1.0f,
    val headingUp: Boolean = true,
    val mapDark: Boolean = false,
    val routeStyle: RouteStyle = RouteStyle.Trekking,
    /** 想定の平均速度(km/h)。補給・休憩の間隔、天気の確認間隔に使う目安 */
    val paceKmh: Double = 18.0,
    /** 補給・休憩スポットを提案する間隔(時間) */
    val restIntervalHours: Double = 1.5,
) {
    companion object {
        val SPEECH_RATES = listOf(0.8f, 1.0f, 1.2f, 1.4f)
        /** ゆっくり・ふつう・速い・競技、の目安speakerペース(km/h) */
        val PACE_PRESETS = listOf(12.0, 18.0, 25.0, 32.0)
        val REST_INTERVAL_PRESETS = listOf(1.0, 1.5, 2.0, 3.0)
    }
}

/** 走った距離と時間を数える(到着したときの案内に使う)。止まっている間の位置のふらつきは、距離に足さない */
class TripTracker(val startMs: Long) {
    var distanceM: Double = 0.0
        private set
    private var last: Fix? = null

    fun onFix(f: Fix) {
        val p = last
        last = f
        if (p == null) return
        val d = GeoMath.distanceMeters(p.latLon, f.latLon)
        val stopped = (f.speedMps ?: 0.0) < 0.5 && (p.speedMps ?: 0.0) < 0.5
        if (stopped && d < max(4.0, f.accuracyM ?: 0.0)) return
        if (d > MAX_JUMP_M) return
        distanceM += d
    }

    fun elapsedMs(nowMs: Long): Long = (nowMs - startMs).coerceAtLeast(0)

    companion object {
        private const val MAX_JUMP_M = 1_000.0
    }
}

/** 到着したときの結果 */
data class ArrivalSummary(val distanceM: Double, val durationMs: Long) {
    val speech: String get() = Phrases.arrival(distanceM, durationMs)
}

/** [NavSession.onFix] の結果 */
data class SessionUpdate(val guidance: GuidanceUpdate, val arrival: ArrivalSummary?, val weatherAlert: String? = null)

/** 1回のナビ(出発から到着まで)。ルート・案内・走行の記録をまとめる */
class NavSession(route: Route, val destination: Place, val startMs: Long, weatherPlan: WeatherPlan? = null) {
    var route: Route = route
        private set
    var engine = GuidanceEngine(route)
        private set
    val trip = TripTracker(startMs)
    var arrival: ArrivalSummary? = null
        private set
    var rerouteCount = 0
        private set
    private var weatherAlert = weatherPlan?.let { WeatherAlertEngine(it.samples, it.weathers) }

    fun onFix(fix: Fix): SessionUpdate {
        trip.onFix(fix)
        val g = engine.update(fix)
        if (g.arrived && arrival == null) arrival = ArrivalSummary(trip.distanceM, trip.elapsedMs(fix.timeMs))
        val alert = weatherAlert?.update(g.progressM)
        return SessionUpdate(g, arrival, alert)
    }

    /** ルートを引き直したとき。走った距離と時間は、そのまま続ける。天気の地点は旧ルート基準のため、以後の声かけはやめる */
    fun reroute(newRoute: Route) {
        route = newRoute
        engine = GuidanceEngine(newRoute)
        weatherAlert = null
        rerouteCount++
    }
}
