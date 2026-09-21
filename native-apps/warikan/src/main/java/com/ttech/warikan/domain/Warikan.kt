package com.ttech.warikan.domain

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

enum class RoundingMode(val label: String) {
    UP("切り上げ(端数は幹事負担なし)"),
    DOWN("切り捨て(端数は幹事負担)"),
    NEAREST("四捨五入"),
}

@Serializable
data class Participant(val id: String, val name: String)

data class WarikanResult(val perPerson: Int, val remainder: Int)

object Warikan {
    fun round(value: Double, mode: RoundingMode): Int = when (mode) {
        RoundingMode.UP -> ceil(value).toInt()
        RoundingMode.DOWN -> floor(value).toInt()
        RoundingMode.NEAREST -> value.roundToInt()
    }

    /** 合計金額を人数で割り、端数処理する。人数が0のときは全額が端数として残る */
    fun calculate(totalYen: Int, participantCount: Int, mode: RoundingMode): WarikanResult {
        if (participantCount <= 0) return WarikanResult(perPerson = 0, remainder = totalYen)
        val perPerson = round(totalYen.toDouble() / participantCount, mode)
        return WarikanResult(perPerson, remainder = totalYen - perPerson * participantCount)
    }
}
