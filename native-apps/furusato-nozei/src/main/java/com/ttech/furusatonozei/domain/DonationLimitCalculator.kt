package com.ttech.furusatonozei.domain

data class CalcInput(
    val salaryIncome: Int,
    val hasSpouse: Boolean,
    val dependents: Int, // 一般の扶養親族(16歳以上)の人数
)

/** 給与所得控除(2020年分以降の速算表) */
fun employmentIncomeDeduction(income: Int): Int = when {
    income <= 1_625_000 -> 550_000
    income <= 1_800_000 -> (income * 0.4 - 100_000).toInt()
    income <= 3_600_000 -> (income * 0.3 + 80_000).toInt()
    income <= 6_600_000 -> (income * 0.2 + 440_000).toInt()
    income <= 8_500_000 -> (income * 0.1 + 1_100_000).toInt()
    else -> 1_950_000
}

/** 所得税の速算表(課税所得に応じた税率) */
fun incomeTaxRate(taxableIncome: Int): Double = when {
    taxableIncome <= 1_949_000 -> 0.05
    taxableIncome <= 3_299_000 -> 0.1
    taxableIncome <= 6_949_000 -> 0.2
    taxableIncome <= 8_999_000 -> 0.23
    taxableIncome <= 17_999_000 -> 0.33
    taxableIncome <= 39_999_000 -> 0.4
    else -> 0.45
}

/** ふるさと納税 寄付限度額の目安(Webのmicrosaas版と同じ計算式) */
fun calcDonationLimit(input: CalcInput): Int {
    val (salaryIncome, hasSpouse, dependents) = input
    if (salaryIncome <= 0) return 0

    val employmentDeduction = employmentIncomeDeduction(salaryIncome)
    val salaryDeductionIncome = maxOf(salaryIncome - employmentDeduction, 0)

    val socialInsuranceDeduction = salaryIncome * 0.15 // 概算(実際の社会保険料により変動)
    val basicDeductionIncomeTax = 480_000
    val basicDeductionResidentTax = 430_000
    val spouseDeductionIncomeTax = if (hasSpouse) 380_000 else 0
    val spouseDeductionResidentTax = if (hasSpouse) 330_000 else 0
    val dependentDeductionIncomeTax = dependents * 380_000
    val dependentDeductionResidentTax = dependents * 330_000

    val taxableIncomeForIncomeTax = maxOf(
        salaryDeductionIncome - socialInsuranceDeduction - basicDeductionIncomeTax -
            spouseDeductionIncomeTax - dependentDeductionIncomeTax,
        0.0,
    )
    val taxableIncomeForResidentTax = maxOf(
        salaryDeductionIncome - socialInsuranceDeduction - basicDeductionResidentTax -
            spouseDeductionResidentTax - dependentDeductionResidentTax,
        0.0,
    )

    val marginalIncomeTaxRate = incomeTaxRate(taxableIncomeForIncomeTax.toInt())
    val residentTaxIncomeLevy = taxableIncomeForResidentTax * 0.1

    val denominator = 0.9 - marginalIncomeTaxRate * 1.021
    if (denominator <= 0) return 0

    val limit = (residentTaxIncomeLevy * 0.2) / denominator + 2000
    return maxOf((limit / 1000).toInt() * 1000, 0)
}
