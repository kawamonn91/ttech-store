package com.ttech.bikenavi.domain

import com.ttech.track.domain.GeoMath
import kotlin.math.abs

/**
 * ルートの折れ線から、曲がり角を見つける。BRouterは(OSRMと違って)完成された「右折です」のような
 * 指示をくれないので、点の並びの方位の変化から自分で判定する。分岐・ロータリー・入口出口の区別は
 * 付けられないので、すべて曲がり角(Turn)として扱う。
 */
object ManeuverDetector {
    /** これ未満の方位の変化は、直進とみなして案内しない */
    private const val TURN_THRESHOLD_DEG = 25.0
    private const val SLIGHT_MAX_DEG = 45.0
    private const val SHARP_MIN_DEG = 120.0
    private const val UTURN_MIN_DEG = 155.0

    /** この距離(m)以内で続けて同じ向きに曲がっていたら、1つの曲がり角としてまとめる(カーブの通過点がいくつもあるため) */
    private const val CLUSTER_GAP_M = 20.0

    /** 出発・到着のごく近くは、短すぎて意味のある曲がり角にならないので無視する */
    private const val EDGE_MARGIN_M = 15.0

    /**
     * [wayTagsAt] は、出発地からの道のり(m)を渡すと、その先の道のタグ(highway=... など)を返す関数。
     * 二段階右折の目安判定(右折先が広い道路かどうか)に使う。無ければ null を返せばよい。
     */
    fun detect(line: Polyline, wayTagsAt: (atM: Double) -> String? = { null }): List<Maneuver> {
        val points = line.points
        if (points.size < 3) return listOf(arrive(line))

        val bearings = DoubleArray(points.size - 1) { GeoMath.bearingDegrees(points[it], points[it + 1]) }
        val candidates = ArrayList<Pair<Int, Double>>() // (点の番号, その点での方位の変化)
        for (i in 1 until bearings.size) {
            val delta = GeoMath.angleDiffDegrees(bearings[i - 1], bearings[i])
            if (abs(delta) > 1.0) candidates.add(i to delta)
        }

        val maneuvers = ArrayList<Maneuver>()
        var i = 0
        while (i < candidates.size) {
            var j = i
            var sum = candidates[i].second
            while (j + 1 < candidates.size && sameSign(sum, candidates[j + 1].second) && gapM(line, candidates[j].first, candidates[j + 1].first) <= CLUSTER_GAP_M) {
                j++
                sum += candidates[j].second
            }
            val atIndex = candidates[j].first
            val atM = line.cumM[atIndex]
            if (abs(sum) >= TURN_THRESHOLD_DEG && atM in EDGE_MARGIN_M..(line.lengthM - EDGE_MARGIN_M)) {
                val modifier = modifierOf(sum)
                maneuvers.add(
                    Maneuver(
                        kind = ManeuverKind.Turn,
                        modifier = modifier,
                        atM = atM,
                        location = points[atIndex],
                        twoStageRightTurn = modifier.contains("right") && isTwoStageRightTurnRoad(wayTagsAt(atM)),
                    ),
                )
            }
            i = j + 1
        }
        maneuvers.add(arrive(line))
        return maneuvers.sortedBy { it.atM }
    }

    private fun gapM(line: Polyline, a: Int, b: Int): Double = line.cumM[b] - line.cumM[a]

    private fun sameSign(a: Double, b: Double) = (a >= 0) == (b >= 0)

    private fun modifierOf(delta: Double): String {
        val mag = abs(delta)
        val dir = if (delta >= 0) "right" else "left"
        return when {
            mag >= UTURN_MIN_DEG -> "uturn"
            mag >= SHARP_MIN_DEG -> "sharp $dir"
            mag >= SLIGHT_MAX_DEG -> dir
            else -> "slight $dir"
        }
    }

    private fun arrive(line: Polyline): Maneuver {
        val last = line.points.size - 1
        val modifier = if (line.points.size >= 3) {
            val bFinal = GeoMath.bearingDegrees(line.points[last - 1], line.points[last])
            val bBefore = GeoMath.bearingDegrees(line.points[(last - 2).coerceAtLeast(0)], line.points[last - 1])
            val delta = GeoMath.angleDiffDegrees(bBefore, bFinal)
            if (abs(delta) < TURN_THRESHOLD_DEG) null else if (delta > 0) "right" else "left"
        } else {
            null
        }
        return Maneuver(ManeuverKind.Arrive, modifier, line.lengthM, line.points.last())
    }

    /** 右折先が、二段階右折の対象になりそうな「広い道路」かどうかの目安(法規上の判定ではない) */
    internal fun isTwoStageRightTurnRoad(wayTags: String?): Boolean {
        if (wayTags == null) return false
        val highway = tag(wayTags, "highway")
        val lanes = tag(wayTags, "lanes")?.toIntOrNull()
        val major = highway in MAJOR_HIGHWAY_TAGS
        return major || (lanes != null && lanes >= 2)
    }

    private fun tag(wayTags: String, key: String): String? =
        wayTags.split(" ").firstOrNull { it.startsWith("$key=") }?.substringAfter("=")

    private val MAJOR_HIGHWAY_TAGS = setOf("trunk", "primary", "secondary", "trunk_link", "primary_link", "secondary_link")
}
