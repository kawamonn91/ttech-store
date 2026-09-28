package com.ttech.navi.domain

import com.ttech.track.domain.GeoMath

object Fixtures {
    fun text(name: String): String = Fixtures::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    /** 実際の OSRM の応答(会津若松駅 → 東山温泉方面、約6km、案内する動作は9個) */
    fun shortRoute(): Route = OsrmParser.parse(text("osrm_short.json"))

    const val START_MS = 1_790_000_000_000L

    /** ルートの上を、一定の速さ(m/秒)で走った、1秒ごとの位置 */
    fun drive(route: Route, speedMps: Double = 10.0, fromM: Double = 0.0, toM: Double = route.distanceM + 20.0): List<Fix> {
        val out = ArrayList<Fix>()
        var p = fromM
        var t = START_MS
        while (p <= toM) {
            val here = route.line.pointAt(p)
            val ahead = route.line.pointAt(p + 5.0)
            out.add(Fix(t, here.lat, here.lon, speedMps, GeoMath.bearingDegrees(here, ahead), 5.0))
            p += speedMps
            t += 1000
        }
        return out
    }
}
