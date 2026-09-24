package com.ttech.restaurantchecklist.domain

import kotlinx.serialization.Serializable

@Serializable
data class StockItem(
    val id: String,
    val name: String,
    /** 必要量 */
    val targetQty: Double,
    /** 現在量 */
    val currentQty: Double = 0.0,
    /** 単位(個、kg、Lなど) */
    val unit: String,
)

/** 入力値から品目を作る。品目名が空、必要量が読めない・0なら作らない(null)。現在量は0から始める。 */
fun buildStockItem(id: String, name: String, targetQty: String, unit: String): StockItem? {
    val target = targetQty.toDoubleOrNull() ?: return null
    if (name.isBlank() || target == 0.0) return null
    return StockItem(id = id, name = name.trim(), targetQty = target, unit = unit)
}

/** 品目を末尾に追加する(Webアプリ版と同じく登録順に並ぶ)。 */
fun List<StockItem>.addItem(item: StockItem): List<StockItem> = this + item

/** 現在量を更新する。マイナスにはしない。 */
fun List<StockItem>.updateQty(id: String, qty: Double): List<StockItem> =
    map { if (it.id == id) it.copy(currentQty = maxOf(qty, 0.0)) else it }

fun StockItem.needsPrep(): Boolean = currentQty < targetQty

/** 仕込みが必要な(現在量が必要量に足りない)品目。 */
fun List<StockItem>.needsPrep(): List<StockItem> = filter { it.needsPrep() }

/** JSの数値表示と同じく、整数なら小数点以下を付けない(3.0 → "3"、1.5 → "1.5")。 */
fun formatQty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
