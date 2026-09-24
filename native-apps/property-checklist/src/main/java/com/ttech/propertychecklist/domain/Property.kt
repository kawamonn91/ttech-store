package com.ttech.propertychecklist.domain

import kotlinx.serialization.Serializable

/** 物件を追加したときの初期チェック項目(Webアプリ版と同じ10項目)。 */
val DEFAULT_LABELS = listOf(
    "日当たり・方角",
    "騒音(道路・線路・近隣)",
    "収納の広さ",
    "水回りの状態",
    "壁・床の傷や汚れ",
    "コンセントの数・位置",
    "携帯電波の入り",
    "共用部の管理状態",
    "駐輪場・駐車場",
    "周辺の買い物環境",
)

@Serializable
data class ChecklistItem(val id: String, val label: String, val checked: Boolean = false)

@Serializable
data class Property(
    val id: String,
    val address: String,
    /** 内見日 ISO8601 (YYYY-MM-DD) */
    val viewedDate: String,
    val items: List<ChecklistItem>,
    val notes: String = "",
)

/** 初期チェック項目付きの物件を作る。IDは [newId] で発行する(テストで固定できるように)。 */
fun newProperty(address: String, viewedDate: String, newId: () -> String): Property = Property(
    id = newId(),
    address = address,
    viewedDate = viewedDate,
    items = DEFAULT_LABELS.map { ChecklistItem(id = newId(), label = it) },
)

fun List<Property>.toggleItem(propertyId: String, itemId: String): List<Property> = map { p ->
    if (p.id != propertyId) p else p.copy(items = p.items.map { if (it.id == itemId) it.copy(checked = !it.checked) else it })
}

fun List<Property>.updateNotes(propertyId: String, notes: String): List<Property> =
    map { if (it.id == propertyId) it.copy(notes = notes) else it }

/** 物件一覧に出す「住所(3/10)」形式のラベル。 */
fun Property.progressLabel(): String = "$address(${items.count { it.checked }}/${items.size})"
