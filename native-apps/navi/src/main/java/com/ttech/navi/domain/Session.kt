package com.ttech.navi.domain

import com.ttech.track.domain.GeoMath
import kotlin.math.max

/** 利用者が変えられる設定 */
data class NaviSettings(
    /** 音声で案内する */
    val voice: Boolean = true,
    /** ナビの開始時に、天気の案内を読み上げる */
    val weatherBriefing: Boolean = true,
    /** 都道府県・市区町村に入ったことを、声で知らせる */
    val regionAnnouncements: Boolean = true,
    /** 読み上げの速さ(1.0が標準) */
    val speechRate: Float = 1.0f,
    /** 進行方向が上になるように、地図を回す */
    val headingUp: Boolean = true,
    val mapDark: Boolean = false,
    /** 自分の車の車格(高速道路の通行料金の目安の計算に使う) */
    val vehicleClass: VehicleClass = VehicleClass.Standard,
) {
    companion object {
        val SPEECH_RATES = listOf(0.8f, 1.0f, 1.2f, 1.4f)
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
        if (d > MAX_JUMP_M) return // 測位の飛び(トンネルの出口など)は、距離に入れない
        distanceM += d
    }

    fun elapsedMs(nowMs: Long): Long = (nowMs - startMs).coerceAtLeast(0)

    companion object {
        private const val MAX_JUMP_M = 3_000.0
    }
}

/** 到着したときの結果 */
data class ArrivalSummary(val distanceM: Double, val durationMs: Long) {
    val speech: String get() = Phrases.arrival(distanceM, durationMs)
}

/** [NavSession.onFix] の結果 */
data class SessionUpdate(val guidance: GuidanceUpdate, val arrival: ArrivalSummary?)

/**
 * 1回のナビ(出発から到着まで)。ルート・案内・走行の記録・地域の検知をまとめる。
 * 時刻は位置(Fix)から受け取る(時計に依存しないので、テストで再現できる)。
 */
class NavSession(route: Route, val destination: Place, val startMs: Long) {
    var route: Route = route
        private set
    var engine = GuidanceEngine(route)
        private set
    val trip = TripTracker(startMs)
    val regions = RegionTracker()
    var arrival: ArrivalSummary? = null
        private set
    var rerouteCount = 0
        private set

    fun onFix(fix: Fix): SessionUpdate {
        trip.onFix(fix)
        val g = engine.update(fix)
        if (g.arrived && arrival == null) arrival = ArrivalSummary(trip.distanceM, trip.elapsedMs(fix.timeMs))
        return SessionUpdate(g, arrival)
    }

    /** ルートを引き直したとき。走った距離と時間は、そのまま続ける */
    fun reroute(newRoute: Route) {
        route = newRoute
        engine = GuidanceEngine(newRoute)
        rerouteCount++
    }

    fun onRegion(region: Region): String? = regions.onRegion(region)
}
