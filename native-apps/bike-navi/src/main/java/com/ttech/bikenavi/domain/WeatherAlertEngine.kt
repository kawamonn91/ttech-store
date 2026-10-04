package com.ttech.bikenavi.domain

/** 出発前に調べた天気予報(地点ごとの予報)。案内中の声かけにも、出発時の案内にも使う */
data class WeatherPlan(val samples: List<SamplePoint>, val weathers: List<Weather?>)

/**
 * 案内中、天気が崩れそうな地点に近づいたら、その場で声で知らせる。
 * 出発前の案内(BriefingService)と同じ予報データを使い、まだ知らせていない地点だけを対象にする。
 * 地点は手前から順に見て、1回の [update] につき1件だけ知らせる(複数地点が同時に近づくことは、間隔的にまず無い)。
 */
class WeatherAlertEngine(
    private val samples: List<SamplePoint>,
    private val weathers: List<Weather?>,
    private val aheadM: Double = AHEAD_M,
) {
    private val announced = HashSet<Int>()

    /** 現在の進み([progressM])を受け取り、近づいた・通り過ぎた地点があれば知らせる文を返す(無ければ null) */
    fun update(progressM: Double): String? {
        for (i in samples.indices) {
            if (i in announced) continue
            val w = weathers.getOrNull(i) ?: continue
            val precip = WeatherCodes.classify(w)
            if (precip == Precip.None) continue
            val sample = samples[i]
            if (sample.progressM < progressM) {
                // 近づく前に通り過ぎてしまった(アプリを閉じていた間など)。今さら知らせても遅いので対象から外す
                announced.add(i)
                continue
            }
            if (sample.progressM - progressM > aheadM) continue
            announced.add(i)
            val where = if (sample.isDestination) "目的地付近で" else "この先"
            return "${where}、${precip.label}が降りそうです。気をつけてください。"
        }
        return null
    }

    companion object {
        /** この距離(m)まで近づいたら知らせる。自転車の速さを踏まえて、数分〜10分強の目安 */
        private const val AHEAD_M = 3_000.0
    }
}
