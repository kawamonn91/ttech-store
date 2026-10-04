package com.ttech.bikenavi.domain

import com.ttech.track.domain.GeoMath

object Fixtures {
    fun text(name: String): String = Fixtures::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    /** 実際のBRouterの応答(渋谷付近、約3.3km) */
    fun shortRoute(): Route = BRouterParser.parse(text("brouter_short.json"))

    const val START_MS = 1_790_000_000_000L

    /** ルートの上を、一定の速さ(m/秒)で走った、1秒ごとの位置 */
    fun ride(route: Route, speedMps: Double = 5.0, fromM: Double = 0.0, toM: Double = route.distanceM + 20.0): List<Fix> {
        val out = ArrayList<Fix>()
        var p = fromM
        var t = START_MS
        while (p <= toM) {
            val here = route.line.pointAt(p)
            val ahead = route.line.pointAt(p + 3.0)
            out.add(Fix(t, here.lat, here.lon, speedMps, GeoMath.bearingDegrees(here, ahead), 5.0))
            p += speedMps
            t += 1000
        }
        return out
    }
}
