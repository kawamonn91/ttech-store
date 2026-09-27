package com.ttech.weightlog.domain

/** 直近の記録を、LINEなどに貼れる短い文章にする */
fun weightSummaryText(stats: Stats, profile: Profile): String = buildString {
    val latest = stats.latest ?: return "まだ記録がありません"
    append("【体重記録】").append(latest.date).append(' ').append(formatKg(latest.weightKg))
    latest.bodyFatPercent?.let { append(" ・ 体脂肪 ").append(formatPercent(it)) }
    append('\n')
    stats.changeFromStart?.let { append("開始から ").append(formatKgSigned(it)).append('\n') }
    stats.changeLast7Days?.let { append("直近7日 ").append(formatKgSigned(it)).append('\n') }
    profile.heightCm?.let { h ->
        Body.bmi(latest.weightKg, h)?.let { bmi -> append("BMI ").append(String.format(java.util.Locale.US, "%.1f", bmi)).append("(").append(Body.bmiCategory(bmi)).append(")\n") }
    }
    profile.goalWeightKg?.let { g ->
        append("目標 ").append(formatKg(g))
        stats.remainingToGoal?.let { r -> append("(あと ").append(formatKg(kotlin.math.abs(r))).append(if (r > 0) ")" else " 達成!)") }
        append('\n')
    }
}.trimEnd() + "\n"

/** 記録の一覧を、日付の新しい順の文章にする */
fun entriesText(entries: List<WeightEntry>): String {
    val out = StringBuilder("【体重の記録】").append(entries.size).append("件\n")
    for (e in sortedByDateDesc(entries)) {
        out.append(e.date).append("  ").append(formatKg(e.weightKg))
        e.bodyFatPercent?.let { out.append("  体脂肪").append(formatPercent(it)) }
        if (e.memo.isNotEmpty()) out.append("  ").append(e.memo)
        out.append('\n')
    }
    return out.toString().trimEnd() + "\n"
}

/** 最初に開いたときに見てもらう、使い方の分かるサンプル(直近30日、ゆるやかに減っていく) */
fun sampleEntries(today: java.time.LocalDate, newId: () -> String, nowMs: Long): List<WeightEntry> {
    val startWeight = 68.5
    return (29 downTo 0 step 2).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        // ゆるい減少 + 小さな上下(体重は日々ふらつくもの、という雰囲気を出す)
        val trend = startWeight - (29 - daysAgo) * 0.05
        val wobble = if (daysAgo % 4 == 0) 0.3 else -0.2
        WeightEntry(
            id = newId(), date = date.toString(), weightKg = Math.round((trend + wobble) * 10) / 10.0,
            bodyFatPercent = 24.0 - (29 - daysAgo) * 0.03, createdAtMs = nowMs, updatedAtMs = nowMs,
        )
    }
}
