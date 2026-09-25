package com.ttech.tripshiori.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 予定の種類。アイコンと集計(費用)に使う */
@Serializable
enum class ItemKind(val label: String) {
    MOVE("移動"),
    SIGHT("観光"),
    MEAL("食事"),
    STAY("宿泊"),
    OTHER("その他"),
}

/** 1つの予定。[day] は 0 始まりの「何日目か」、[time] は "HH:mm"(未定なら空) */
@Serializable
data class ScheduleItem(
    val id: String,
    val day: Int,
    val time: String = "",
    val title: String,
    val place: String = "",
    val memo: String = "",
    val kind: ItemKind = ItemKind.SIGHT,
    /** かかる費用(円)。0 なら未設定 */
    val cost: Int = 0,
)

@Serializable
data class PackingItem(val id: String, val name: String, val checked: Boolean = false)

@Serializable
data class Lodging(
    val id: String,
    val name: String,
    val address: String = "",
    val phone: String = "",
    val reservation: String = "",
    /** チェックイン・チェックアウトなどの自由記入(例: 15:00〜 / 11:00 まで) */
    val times: String = "",
    val note: String = "",
)

@Serializable
data class Contact(val id: String, val name: String, val phone: String = "", val note: String = "")

/** 1つの旅のしおり。日付は ISO(yyyy-MM-dd) */
@Serializable
data class Trip(
    val id: String,
    val title: String,
    val destination: String = "",
    val startDate: String,
    val endDate: String,
    val travelers: List<String> = emptyList(),
    val notes: String = "",
    val items: List<ScheduleItem> = emptyList(),
    val packing: List<PackingItem> = emptyList(),
    val lodgings: List<Lodging> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val updatedAtMs: Long = 0,
)

/**
 * 保存・ファイルの読み書きに使う JSON。
 * 知らない項目は無視し、知らない種類(enum)は既定値に戻す(新しい版のファイルを古い版で読んでも壊れない)。
 */
val TripJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}
