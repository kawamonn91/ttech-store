package com.ttech.weightlog.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

object Limits {
    const val MAX_ENTRIES = 4000
    const val MAX_MEMO = 500
    val WEIGHT_RANGE = 20.0..300.0
    val BODY_FAT_RANGE = 1.0..70.0
    val HEIGHT_RANGE = 80.0..250.0
}

fun parseDateOrNull(text: String): LocalDate? = try { LocalDate.parse(text) } catch (_: DateTimeParseException) { null }

fun formatKg(value: Double): String = String.format(Locale.US, "%.1f kg", value)
fun formatKgSigned(value: Double): String = (if (value > 0) "+" else "") + String.format(Locale.US, "%.1f kg", value)
fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)

// ---------------------------------------------------------------- BMI・標準体重(日本肥満学会の基準)

object Body {
    /** 体格指数(BMI)。身長・体重が正しくないときは null */
    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 0 || heightCm <= 0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

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
}

// ---------------------------------------------------------------- 並び順・追加

/** 日付の新しい順 */
fun sortedByDateDesc(entries: List<WeightEntry>): List<WeightEntry> = entries.sortedByDescending { it.date }

/** 日付の古い順(グラフ用) */
fun sortedByDateAsc(entries: List<WeightEntry>): List<WeightEntry> = entries.sortedBy { it.date }

/** 同じ日付の記録を1件に置き換える(idが違っても、日付が同じなら上書きする) */
fun upsertByDate(entries: List<WeightEntry>, entry: WeightEntry): List<WeightEntry> {
    val others = entries.filterNot { it.date == entry.date }
    return others + entry
}

// ---------------------------------------------------------------- 統計

data class Stats(
    val latest: WeightEntry?,
    val start: WeightEntry?,
    /** 開始からの増減(kg)。マイナスが減量 */
    val changeFromStart: Double?,
    /** 直近7日の増減(記録が2件未満なら null) */
    val changeLast7Days: Double?,
    /** 目標まで、あと何kg(マイナスなら目標を超えて達成)。目標未設定なら null */
    val remainingToGoal: Double?,
    /** 開始から目標までを100%として、いまどこまで来たか(0〜100超もありうる)。目標未設定・開始と目標が同じなら null */
    val progressPercent: Double?,
    /** 記録した日数が連続で何日続いているか(今日 or 昨日を起点に数える) */
    val streakDays: Int,
    val totalDays: Int,
)

fun computeStats(entries: List<WeightEntry>, profile: Profile, today: LocalDate): Stats {
    val sorted = sortedByDateAsc(entries)
    val latest = sorted.lastOrNull()
    val start = sorted.firstOrNull()
    val changeFromStart = if (latest != null && start != null && latest.id != start.id) latest.weightKg - start.weightKg else null

    val cutoff = today.minusDays(7)
    val within7 = sorted.filter { parseDateOrNull(it.date)?.let { d -> !d.isBefore(cutoff) } == true }
    val changeLast7 = if (within7.size >= 2) within7.last().weightKg - within7.first().weightKg else null

    val goal = profile.goalWeightKg
    val remaining = if (latest != null && goal != null) latest.weightKg - goal else null
    val progress = if (latest != null && start != null && goal != null && start.weightKg != goal) {
        ((start.weightKg - latest.weightKg) / (start.weightKg - goal)) * 100.0
    } else {
        null
    }

    return Stats(
        latest = latest, start = start, changeFromStart = changeFromStart, changeLast7Days = changeLast7,
        remainingToGoal = remaining, progressPercent = progress, streakDays = streakDays(entries, today), totalDays = entries.size,
    )
}

/** 記録した日の連続日数。今日か昨日に記録が無ければ0(今日はまだ記録していなくても、昨日まで続いていれば数える) */
fun streakDays(entries: List<WeightEntry>, today: LocalDate): Int {
    val dates = entries.mapNotNull { parseDateOrNull(it.date) }.toSet()
    var cursor = if (today in dates) today else today.minusDays(1)
    if (cursor !in dates) return 0
    var count = 0
    while (cursor in dates) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

// ---------------------------------------------------------------- 整形(保存・読み込みしても安全な形に)

fun WeightEntry.sanitized(nowMs: Long = updatedAtMs): WeightEntry {
    val date = parseDateOrNull(date)?.toString() ?: LocalDate.of(2000, 1, 1).toString()
    return copy(
        id = id.trim().take(80),
        date = date,
        weightKg = weightKg.coerceIn(Limits.WEIGHT_RANGE),
        bodyFatPercent = bodyFatPercent?.coerceIn(Limits.BODY_FAT_RANGE),
        memo = memo.trim().take(Limits.MAX_MEMO),
        updatedAtMs = nowMs,
    )
}

fun Profile.sanitized(): Profile = copy(
    heightCm = heightCm?.coerceIn(Limits.HEIGHT_RANGE),
    goalWeightKg = goalWeightKg?.coerceIn(Limits.WEIGHT_RANGE),
    goalDate = goalDate?.let { parseDateOrNull(it)?.toString() },
)
