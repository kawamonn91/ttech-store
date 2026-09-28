package com.ttech.navi.domain

import kotlin.math.max
import kotlin.math.min

/** 声で伝える一言。[maneuverIndex] は、どの動作の案内か(地域などの案内は null) */
data class Announcement(val text: String, val maneuverIndex: Int? = null)

/** 位置を1回受け取るたびに返す、案内の状況 */
data class GuidanceUpdate(
    /** 出発地からの道のり(m)。ルートから外れている間は増えない */
    val progressM: Double,
    val remainingM: Double,
    val remainingS: Double,
    /** ルートの折れ線までの距離(m) */
    val offsetM: Double,
    /** いちばん近い、これから案内する動作と、そこまでの距離 */
    val next: Maneuver?,
    val distToNextM: Double?,
    /** その次の動作(「その後〜」の案内に使う)と、いまの動作からその動作までの距離 */
    val afterNext: Maneuver?,
    val gapM: Double?,
    val announcements: List<Announcement>,
    /** ルートから外れたと判断した(再検索が必要) */
    val offRoute: Boolean,
    /** 目的地に着いた(最初に着いたと判断した1回だけ true) */
    val arrived: Boolean,
)

/**
 * 位置を受け取って、ルート上のどこにいるかを求め、次の曲がり角などを、距離に応じて案内する。
 *
 * ルール:
 *  - 動作までの距離が、2km・1km・300m・「まもなく」(速さで80〜250m)を切るたびに1回ずつ案内する。
 *    最初に見えたときに、すでに近い場合は、その距離で1回だけ言う(遠い段階は飛ばす)
 *  - 案内には、その次の動作(もう一つ先の曲がる方向)を「その後、○○メートル先、左折です」と添える
 *    (次の動作までが遠い場合は、添えない)
 *  - ルートから外れたら、古い案内はやめる。外れた判断は [Config.offRouteCount] 回続いたとき
 */
class GuidanceEngine(val route: Route, private val config: Config = Config()) {
    data class Config(
        /** ルートからこの距離(m)以上はなれたら「外れているかも」 */
        val offRouteM: Double = 45.0,
        /** 「外れているかも」が、この回数(1秒に1回の測位なら秒数)続いたら、外れたと判断する */
        val offRouteCount: Int = 5,
        /** 目的地までの道のりがこれ以下(m)なら、到着 */
        val arriveM: Double = 35.0,
        /** 止まっていて、目的地の近く(m)なら、到着とみなす */
        val arriveStoppedM: Double = 90.0,
        /** 「その後〜」を添える、動作どうしの距離の上限(m)。1km以上のときは、これより近いときだけ添える */
        val thenMaxGapM: Double = 2000.0,
        val thenMaxGapFarM: Double = 500.0,
    )

    var progressM: Double = 0.0
        private set

    private var nextIndex = 0
    private var offCount = 0
    private var stoppedNearCount = 0
    private var arrivedFlag = false
    private val announcedLevel = HashMap<Int, Double>()
    private val lastAnnouncedDistance = HashMap<Int, Double>()
    /** 動作ごとに、最後に言った距離の言い方(丸めると同じ言い方になるものを、続けて言わないため) */
    private val lastPhrase = HashMap<Int, String>()

    fun update(fix: Fix): GuidanceUpdate {
        val line = route.line
        val here = fix.latLon

        // 前回の位置の近くから探す(ルートが行って戻るような形でも、別の場所に飛ばないように)。見つからなければ全体から探す
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
        val moving = (fix.speedMps ?: 5.0) > 1.0
        val offRoute = offCount >= config.offRouteCount && moving

        while (nextIndex < route.maneuvers.size - 1 && route.maneuvers[nextIndex].atM < progressM - PASSED_MARGIN_M) nextIndex++
        val next = route.maneuvers.getOrNull(nextIndex)
        val distToNext = next?.let { (it.atM - progressM).coerceAtLeast(0.0) }
        val after = route.maneuvers.getOrNull(nextIndex + 1)
        val gap = if (next != null && after != null) (after.atM - next.atM).coerceAtLeast(0.0) else null

        val remaining = route.remainingM(progressM)
        val speech = ArrayList<Announcement>()
        if (onRoute || offCount < 2) {
            if (next != null && distToNext != null) announceLevel(nextIndex, next, distToNext, after, gap, fix.speedMps)?.let { speech.add(it) }
        }

        var arrived = false
        if (!arrivedFlag && onRoute) {
            val stoppedNear = remaining <= config.arriveStoppedM && (fix.speedMps ?: 5.0) < 1.0
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
            afterNext = after,
            gapM = gap,
            announcements = speech,
            offRoute = offRoute,
            arrived = arrived,
        )
    }

    private fun announceLevel(index: Int, m: Maneuver, d: Double, after: Maneuver?, gap: Double?, speedMps: Double?): Announcement? {
        val near = ((speedMps ?: 8.0) * 7.0).coerceIn(80.0, 250.0)
        val levels = listOf(2000.0, 1000.0, 300.0, min(near, 250.0)).distinct().sortedDescending()
        // いま通り過ぎようとしている(近すぎる)ものは、もう言わない
        if (d < MIN_ANNOUNCE_M) return null
        val level = levels.filter { d <= it }.minOrNull() ?: return null
        val done = announcedLevel[index]
        if (done != null && level >= done) return null
        // 直前の案内から少ししか進んでいないのに、続けて言わない(最初に見えたとき、近い距離で2回言ってしまうのを防ぐ)
        val last = lastAnnouncedDistance[index]
        if (last != null && last - d < MIN_REPEAT_GAP_M) {
            announcedLevel[index] = level
            return null
        }
        val isNear = level == levels.last() && level <= 250.0
        // 例: 1230m手前で「1キロ先」と言ったあと、990mを切ったところでも「1キロ先」と、同じことを言わない
        val phrase = if (isNear) "near" else Phrases.distance(d)
        if (lastPhrase[index] == phrase) {
            announcedLevel[index] = level
            return null
        }
        announcedLevel[index] = level
        lastAnnouncedDistance[index] = d
        lastPhrase[index] = phrase

        val sb = StringBuilder(Phrases.announce(d, m, isNear))
        if (after != null && gap != null && m.kind != ManeuverKind.Arrive) {
            // 遠くの案内のとき・その次が目的地で遠いときは、「その後〜」を添えない(かえって聞き取りにくいため)
            val limit = if (level > 300.0 || after.kind == ManeuverKind.Arrive) config.thenMaxGapFarM else config.thenMaxGapM
            if (gap <= limit) sb.append(Phrases.then(gap, after))
        }
        return Announcement(sb.toString(), index)
    }

    companion object {
        /** 前回の位置から、このぶん先まで探す(m) */
        private const val WINDOW_AHEAD_M = 3000.0
        /** 動作の地点をこれ以上(m)過ぎたら、次の動作を「次」にする */
        private const val PASSED_MARGIN_M = 15.0
        private const val MIN_ANNOUNCE_M = 40.0
        private const val MIN_REPEAT_GAP_M = 100.0
        private const val STOPPED_ARRIVE_COUNT = 8
    }
}
