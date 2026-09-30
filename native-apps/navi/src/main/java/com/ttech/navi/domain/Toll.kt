package com.ttech.navi.domain

/** 高速道路の料金区分(NEXCOなどの車種区分)。[tollFactor] は、普通車を1.0とした通行料金の目安の倍率 */
enum class VehicleClass(val label: String, val tollFactor: Double) {
    Kei("軽自動車等", 0.8),
    Standard("普通車", 1.0),
    Medium("中型車", 1.2),
    Large("大型車", 1.65),
    ExtraLarge("特大車", 2.75),
}

/**
 * ルートの一部(OSRMの1区間)が、有料の高速道路・自動車専用道路かどうかの目安の判定。
 * OSRMの応答には「有料かどうか」そのものは含まれないので、日本の地図データでの慣例
 * (高速道路の路線番号は "E" から始まる。例: E1, E83)と、道路名から判断する。
 * バイパスなど、無料の自動車専用道路まで有料と判定してしまうことがある、あくまで目安。
 */
fun isTollRoad(ref: String?, name: String): Boolean {
    val refs = ref?.split(";")?.map { it.trim() }.orEmpty()
    if (refs.any { EXPRESSWAY_REF.matches(it) }) return true
    return name.contains("高速") || name.contains("自動車道")
}

private val EXPRESSWAY_REF = Regex("""E\d.*""")

/**
 * 高速道路の通行料金のおおまかな見積もり。NEXCOなどが公表している対距離料金の目安の式
 * (1kmあたりの単価 × 距離 + ターミナルチャージ、に車種の倍率をかける)を使う。
 * 首都高速・阪神高速など対距離制ではない区間、深夜割引などの各種割引・上限額は考慮しないため、
 * 実際の料金とは異なる(あくまで目安の)見積もりであることに注意。
 */
object TollEstimate {
    private const val YEN_PER_KM = 25.0
    private const val TERMINAL_CHARGE_YEN = 150.0

    /** ごく短い区間(入口のランプなど)しか通らないときは、通行料金は無いものとみなす */
    private const val MIN_TOLL_DISTANCE_M = 500.0

    /** [tollDistanceM] だけ高速道路を通るときの、目安の料金(円。10円単位)。高速道路を通らなければ0 */
    fun estimate(tollDistanceM: Double, vehicleClass: VehicleClass): Int {
        if (tollDistanceM < MIN_TOLL_DISTANCE_M) return 0
        val km = tollDistanceM / 1000.0
        val yen = (YEN_PER_KM * km + TERMINAL_CHARGE_YEN) * vehicleClass.tollFactor
        return (Math.round(yen / 10.0) * 10).toInt()
    }
}

/**
 * OSRMから複数の経路の候補が返ったとき、どれを既定として選ぶかを決める。
 * いちばん早い経路を基本にしつつ、大差なく(所要時間が[LEFT_TURN_MAX_EXTRA]以内)目的地に
 * 左折で入れる候補があれば、そちらを選ぶ(右折での進入は、対向車線を横切る分だけ難しいため)。
 */
object RouteChooser {
    private const val LEFT_TURN_MAX_EXTRA = 1.15

    fun pickDefault(candidates: List<Route>): Route {
        require(candidates.isNotEmpty()) { "経路の候補がありません" }
        val fastest = candidates.minBy { it.durationS }
        val leftTurn = candidates
            .filter { it.maneuvers.lastOrNull()?.modifier != "right" }
            .filter { it.durationS <= fastest.durationS * LEFT_TURN_MAX_EXTRA }
            .minByOrNull { it.durationS }
        return leftTurn ?: fastest
    }
}
