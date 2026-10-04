package com.ttech.bikenavi.domain

import kotlin.math.max
import kotlin.math.min

/** 声で伝える一言。[maneuverIndex] は、どの動作の案内か */
data class Announcement(val text: String, val maneuverIndex: Int? = null)

/** 位置を1回受け取るたびに返す、案内の状況 */
data class GuidanceUpdate(
    val progressM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val offsetM: Double,
    val next: Maneuver?,
    val distToNextM: Double?,
    val announcements: List<Announcement>,
    val offRoute: Boolean,
    val arrived: Boolean,
)

/**
 * 位置を受け取って、ルート上のどこにいるかを求め、次の曲がり角を距離に応じて案内する。
 * 自転車は速さが車よりかなり遅いので、案内の距離(500m・まもなく)は navi より近めにしてある。
 */
class GuidanceEngine(val route: Route, private val config: Config = Config()) {
    data class Config(
        val offRouteM: Double = 35.0,
        val offRouteCount: Int = 5,
        val arriveM: Double = 25.0,
        val arriveStoppedM: Double = 60.0,
    )

    var progressM: Double = 0.0
        private set

    private var nextIndex = 0
    private var offCount = 0
    private var stoppedNearCount = 0
    private var arrivedFlag = false
    private val announcedLevel = HashMap<Int, Double>()
    private val lastAnnouncedDistance = HashMap<Int, Double>()
    private val lastPhrase = HashMap<Int, String>()

    fun update(fix: Fix): GuidanceUpdate {
        val line = route.line
        val here = fix.latLon

        val seg = line.segmentAt(progressM)
        var proj = line.project(here, from = max(0, seg - 4), to = min(line.segmentCount - 1, line.segmentAt(progressM + WINDOW_AHEAD_M) + 1))
        val threshold = max(config.offRouteM, (fix.accuracyM ?: 0.0) * 2.0)
        if (proj.distM > threshold) {
            val whole = line.project(here)
            if (whole.distM <= threshold && whole.progressM > progressM - 100.0) proj = whole
        }

        val onRoute = proj.distM <= threshold
        if (onRoute) {
            offCount = 0
            progressM = max(progressM, proj.progressM)
        } else {
            offCount++
        }
        val moving = (fix.speedMps ?: 3.0) > 0.5
        val offRoute = offCount >= config.offRouteCount && moving

        while (nextIndex < route.maneuvers.size - 1 && route.maneuvers[nextIndex].atM < progressM - PASSED_MARGIN_M) nextIndex++
        val next = route.maneuvers.getOrNull(nextIndex)
        val distToNext = next?.let { (it.atM - progressM).coerceAtLeast(0.0) }

        val remaining = route.remainingM(progressM)
        val speech = ArrayList<Announcement>()
        if (onRoute || offCount < 2) {
            if (next != null && distToNext != null) announceLevel(nextIndex, next, distToNext, fix.speedMps)?.let { speech.add(it) }
        }

        var arrived = false
        if (!arrivedFlag && onRoute) {
            val stoppedNear = remaining <= config.arriveStoppedM && (fix.speedMps ?: 3.0) < 0.5
            stoppedNearCount = if (stoppedNear) stoppedNearCount + 1 else 0
            if (remaining <= config.arriveM || stoppedNearCount >= STOPPED_ARRIVE_COUNT) {
                arrivedFlag = true
                arrived = true
            }
        }

        return GuidanceUpdate(
            progressM = progressM,
            remainingM = remaining,
            remainingS = route.remainingS(progressM),
            offsetM = proj.distM,
            next = next,
            distToNextM = distToNext,
            announcements = speech,
            offRoute = offRoute,
            arrived = arrived,
        )
    }

    private fun announceLevel(index: Int, m: Maneuver, d: Double, speedMps: Double?): Announcement? {
        val near = ((speedMps ?: 4.0) * 6.0).coerceIn(30.0, 100.0)
        val levels = listOf(500.0, near).distinct().sortedDescending()
        if (d < MIN_ANNOUNCE_M) return null
        val level = levels.filter { d <= it }.minOrNull() ?: return null
        val done = announcedLevel[index]
        if (done != null && level >= done) return null
        val last = lastAnnouncedDistance[index]
        if (last != null && last - d < MIN_REPEAT_GAP_M) {
            announcedLevel[index] = level
            return null
        }
        val isNear = level == levels.last()
        val phrase = if (isNear) "near" else Phrases.distance(d)
        if (lastPhrase[index] == phrase) {
            announcedLevel[index] = level
            return null
        }
        announcedLevel[index] = level
        lastAnnouncedDistance[index] = d
        lastPhrase[index] = phrase
        return Announcement(Phrases.announce(d, m, isNear), index)
    }

    companion object {
        private const val WINDOW_AHEAD_M = 1500.0
        private const val PASSED_MARGIN_M = 10.0
        private const val MIN_ANNOUNCE_M = 15.0
        private const val MIN_REPEAT_GAP_M = 40.0
        private const val STOPPED_ARRIVE_COUNT = 8
    }
}
