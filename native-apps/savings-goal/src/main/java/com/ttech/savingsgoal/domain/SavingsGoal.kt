package com.ttech.savingsgoal.domain

/** 50年分。これを超える場合は「到達しない」として扱う(Webアプリ版と同じ上限)。 */
const val MAX_MONTHS = 600

/** 積立目標の計算ロジック。Webのmicrosaas版と同じ計算式(月複利・月末積立)を使う。 */
object SavingsGoal {
    /**
     * 目標金額に届くまでの月数。すでに届いていれば0、[MAX_MONTHS]以内に届かなければnull。
     * 目標が0以下・積立額が負の場合も計算しない(null)。
     */
    fun monthsToReachGoal(goal: Double, initial: Double, monthly: Double, annualRatePercent: Double): Int? {
        if (goal <= 0 || monthly < 0) return null
        val monthlyRate = annualRatePercent / 100 / 12
        var balance = initial
        if (balance >= goal) return 0
        for (month in 1..MAX_MONTHS) {
            balance = balance * (1 + monthlyRate) + monthly
            if (balance >= goal) return month
        }
        return null
    }

    /** 月数を「○年○ヶ月」表記にする(端数の月が0なら「○年」だけ)。 */
    fun formatDuration(months: Int): String {
        val years = months / 12
        val rest = months % 12
        return "${years}年" + if (rest != 0) "${rest}ヶ月" else ""
    }
}
