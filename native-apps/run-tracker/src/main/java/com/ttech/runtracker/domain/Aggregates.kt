package com.ttech.runtracker.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** ランの合計 */
data class Totals(
    val count: Int,
    val distanceM: Double,
    val movingMs: Long,
    val elevationGainM: Double,
    val caloriesKcal: Double,
) {
    val avgPaceSecPerKm: Double get() = if (distanceM > 0 && movingMs > 0) movingMs / 1000.0 / (distanceM / 1000.0) else 0.0
}

/** 1週間ぶんの距離(週は月曜から) */
data class WeekBucket(val startMs: Long, val distanceM: Double, val count: Int)

/** ある距離での自己ベストと、それを出したラン */
data class PersonalBest(val def: EffortDef, val timeMs: Long, val runId: String, val startTimeMs: Long) {
    val paceSecPerKm: Double get() = timeMs / 1000.0 / (def.meters / 1000.0)
}

/** 一覧や成績の画面に出す、週・月・年ごとの集計と自己ベスト。日付の区切りは日本時間 */
object Aggregates {
    val JST: ZoneId = ZoneId.of("Asia/Tokyo")

    fun totals(runs: List<RunSummary>): Totals = Totals(
        count = runs.size,
        distanceM = runs.sumOf { it.distanceM },
        movingMs = runs.sumOf { it.movingMs },
        elevationGainM = runs.sumOf { it.elevationGainM },
        caloriesKcal = runs.sumOf { it.caloriesKcal },
    )

    private fun date(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** その日を含む週の、月曜0時(エポックms) */
    fun startOfWeek(ms: Long, zone: ZoneId = JST): Long =
        date(ms, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()

    fun inWeek(runs: List<RunSummary>, nowMs: Long, zone: ZoneId = JST): List<RunSummary> {
        val thisWeek = startOfWeek(nowMs, zone)
        return runs.filter { startOfWeek(it.startTimeMs, zone) == thisWeek }
    }

    fun inMonth(runs: List<RunSummary>, nowMs: Long, zone: ZoneId = JST): List<RunSummary> {
        val month = YearMonth.from(date(nowMs, zone))
        return runs.filter { YearMonth.from(date(it.startTimeMs, zone)) == month }
    }

    fun inYear(runs: List<RunSummary>, nowMs: Long, zone: ZoneId = JST): List<RunSummary> {
        val year = date(nowMs, zone).year
        return runs.filter { date(it.startTimeMs, zone).year == year }
    }

    /** 今週の、月曜〜日曜それぞれの距離(m)。0番目が月曜 */
    fun daysOfWeek(runs: List<RunSummary>, nowMs: Long, zone: ZoneId = JST): DoubleArray {
        val monday = date(startOfWeek(nowMs, zone), zone)
        val out = DoubleArray(7)
        for (r in runs) {
            val offset = java.time.temporal.ChronoUnit.DAYS.between(monday, date(r.startTimeMs, zone)).toInt()
            if (offset in 0..6) out[offset] += r.distanceM
        }
        return out
    }

    /** 直近 [weeks] 週の、週ごとの距離。古い週が先頭、今週が最後 */
    fun weekly(runs: List<RunSummary>, nowMs: Long, weeks: Int = 12, zone: ZoneId = JST): List<WeekBucket> {
        val thisMonday = date(startOfWeek(nowMs, zone), zone)
        val starts = (weeks - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
        val byWeek = runs.groupBy { date(startOfWeek(it.startTimeMs, zone), zone) }
        return starts.map { monday ->
            val list = byWeek[monday].orEmpty()
            WeekBucket(monday.atStartOfDay(zone).toInstant().toEpochMilli(), list.sumOf { it.distanceM }, list.size)
        }
    }

    /** 距離ごとの自己ベスト。同じ記録のときは、先に出したほうを残す */
    fun personalBests(runs: List<RunSummary>): List<PersonalBest> {
        val out = ArrayList<PersonalBest>()
        for (def in EffortDef.entries) {
            var best: PersonalBest? = null
            for (r in runs.sortedBy { it.startTimeMs }) {
                val e = r.effort(def) ?: continue
                if (best == null || e.timeMs < best.timeMs) best = PersonalBest(def, e.timeMs, r.id, r.startTimeMs)
            }
            best?.let(out::add)
        }
        return out
    }

    /** この記録が、自己ベストを出した距離(それまでの記録より速かったもの)。1回目のランは、全部が自己ベストになるので、出さない */
    fun recordsSetBy(run: RunSummary, all: List<RunSummary>): List<EffortDef> {
        val earlier = all.filter { it.startTimeMs < run.startTimeMs }
        if (earlier.isEmpty()) return emptyList()
        return run.efforts.mapNotNull { e ->
            val def = e.def ?: return@mapNotNull null
            val prev = earlier.mapNotNull { it.effort(def)?.timeMs }.minOrNull()
            if (prev == null || e.timeMs < prev) def else null
        }
    }
}

/** 体重・身長から求める指標 */
object Body {
    /** 体格指数(BMI)。身長・体重が正しくないときは null */
    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 0 || heightCm <= 0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    /** 日本肥満学会の判定 */
    fun bmiCategory(bmi: Double): String = when {
        bmi < 18.5 -> "低体重(やせ)"
        bmi < 25.0 -> "普通体重"
        bmi < 30.0 -> "肥満(1度)"
        bmi < 35.0 -> "肥満(2度)"
        bmi < 40.0 -> "肥満(3度)"
        else -> "肥満(4度)"
    }

    /** 標準体重(BMI 22 になる体重) */
    fun idealWeightKg(heightCm: Double): Double? = if (heightCm > 0) 22.0 * (heightCm / 100.0) * (heightCm / 100.0) else null

    /**
     * その時点の体重。記録のうち、その時刻以前でいちばん新しいもの。
     * それ以前の記録が無ければ、いちばん古い記録を使う。記録が無ければ null。
     */
    fun weightAt(entries: List<WeightEntry>, timeMs: Long): Double? {
        if (entries.isEmpty()) return null
        val sorted = entries.sortedBy { it.timeMs }
        return (sorted.lastOrNull { it.timeMs <= timeMs } ?: sorted.first()).weightKg
    }

    /** 入力できる範囲(kg / cm) */
    val WEIGHT_RANGE = 20.0..300.0
    val HEIGHT_RANGE = 80.0..250.0
}
