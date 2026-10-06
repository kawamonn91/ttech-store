package com.ttech.navi.domain

/** 目的地の確認画面に出す提案。[title] は「有料道路を使う・最短」などの見出し */
data class RouteProposal(val route: Route, val title: String)

/**
 * 経路の候補から、画面に出す提案を作る。
 *
 * 有料の高速道路を使う候補と、使わない候補のそれぞれについて、次の順に考える。
 * - 所要時間がいちばん短い経路(最短)を選ぶ
 * - それが目的地に左折で入れる経路なら、それだけを出す(左折進入と最短が一致している)
 * - 右折で入る場合は、最短の [LEFT_TURN_MAX_EXTRA] 倍以内の時間で左折で入れる経路があれば、
 *   それも「左折進入優先」として出す(無ければ最短だけ)
 *
 * 結果は最大で4件(有料道路あり・なし、それぞれ最短と左折進入優先)。
 */
object RouteProposer {
    /** 左折で入るために許す、最短からの所要時間の倍率 */
    private const val LEFT_TURN_MAX_EXTRA = 1.15

    fun propose(candidates: List<Route>): List<RouteProposal> {
        val withTolls = candidates.filter { !it.tollFree }
        val withoutTolls = candidates.filter { it.tollFree }
        return proposeFor(withTolls, "有料道路を使う") + proposeFor(withoutTolls, "有料道路を使わない")
    }

    private fun proposeFor(group: List<Route>, prefix: String): List<RouteProposal> {
        if (group.isEmpty()) return emptyList()
        val shortest = group.minBy { it.durationS }
        if (isLeftEntry(shortest)) return listOf(RouteProposal(shortest, "$prefix・最短(左折で進入)"))

        val leftTurn = group
            .filter { isLeftEntry(it) && it.durationS <= shortest.durationS * LEFT_TURN_MAX_EXTRA }
            .minByOrNull { it.durationS }
        val out = mutableListOf(RouteProposal(shortest, "$prefix・最短(右折で進入)"))
        if (leftTurn != null) out += RouteProposal(leftTurn, "$prefix・左折進入優先")
        return out
    }

    /** 目的地に左折(または直進)で入れるか。右折で入る場合だけ false */
    private fun isLeftEntry(route: Route): Boolean = route.maneuvers.lastOrNull()?.modifier != "right"
}
