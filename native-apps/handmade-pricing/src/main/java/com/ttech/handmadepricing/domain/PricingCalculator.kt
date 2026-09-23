package com.ttech.handmadepricing.domain

import kotlin.math.ceil
import kotlin.math.roundToInt

data class Material(val id: String, val name: String, val cost: Int)

data class PricingInput(
    val materials: List<Material>,
    val workMinutes: Int,
    val hourlyWage: Int,
    val marginPercent: Int,
    val feePercent: Int,
)

data class PricingResult(
    val materialTotal: Int,
    val laborCost: Int,
    val baseCost: Int,
    val suggestedPrice: Int,
    val profit: Int,
)

fun isValidMaterial(name: String, cost: Int): Boolean = name.isNotBlank() && cost != 0

fun calcPricing(input: PricingInput): PricingResult {
    val materialTotal = input.materials.sumOf { it.cost }
    val laborCost = ((input.workMinutes / 60.0) * input.hourlyWage).roundToInt()
    val baseCost = materialTotal + laborCost
    val priceBeforeFee = (baseCost * (1 + input.marginPercent / 100.0)).roundToInt()

    val fee = input.feePercent / 100.0
    val suggestedPrice = if (fee >= 1) {
        priceBeforeFee
    } else {
        (ceil(priceBeforeFee / (1 - fee) / 10) * 10).toInt()
    }

    val profit = suggestedPrice - baseCost - (suggestedPrice * fee).roundToInt()

    return PricingResult(
        materialTotal = materialTotal,
        laborCost = laborCost,
        baseCost = baseCost,
        suggestedPrice = suggestedPrice,
        profit = profit,
    )
}
