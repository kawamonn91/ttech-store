package com.ttech.campchecklist.domain

import kotlinx.serialization.Serializable

/** 新規作成時の初期チェックリスト(Webアプリ版と同じ品目)。 */
val DEFAULT_ITEM_NAMES = listOf("テント", "寝袋", "マット", "ランタン", "クッカー", "チェア", "焚き火台", "着替え")

@Serializable
data class GearItem(val id: String, val name: String, val packed: Boolean = false)

/** 準備できた数/全体数(進捗カード用)。 */
fun packedProgress(items: List<GearItem>): Pair<Int, Int> = items.count { it.packed } to items.size

fun toggleItem(items: List<GearItem>, id: String): List<GearItem> =
    items.map { if (it.id == id) it.copy(packed = !it.packed) else it }

fun removeItem(items: List<GearItem>, id: String): List<GearItem> = items.filterNot { it.id == id }

/** 次回のキャンプに備えて全部チェックを外す(道具自体は消さない)。 */
fun resetAllPacked(items: List<GearItem>): List<GearItem> = items.map { it.copy(packed = false) }
