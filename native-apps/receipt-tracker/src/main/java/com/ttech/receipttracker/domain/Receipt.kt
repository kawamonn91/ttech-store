package com.ttech.receipttracker.domain

import kotlinx.serialization.Serializable

/** 経費のカテゴリ(Webアプリ版と同じ6種)。 */
val CATEGORIES = listOf("交通費", "会議費", "消耗品", "交際費", "通信費", "その他")

@Serializable
data class Receipt(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val amount: Long,
    val category: String,
    val memo: String,
    /**
     * アプリ専用領域に保存したレシート画像のファイル名。画像なしならnull。
     * Webアプリ版は画像をData URLのまま保存していたが、DataStoreに大きな文字列を入れないようファイルに分ける。
     */
    val imageFile: String? = null,
)

/** 入力値から記録を作る。金額が0・読めない場合は作らない(null)。メモは前後の空白を除く。 */
fun buildReceipt(id: String, date: String, amount: String, category: String, memo: String, imageFile: String?): Receipt? {
    val n = amount.toLongOrNull() ?: return null
    if (n == 0L) return null
    return Receipt(id, date, n, category, memo.trim(), imageFile)
}

fun List<Receipt>.total(): Long = sumOf { it.amount }

/** CSVに書き出す(Webアプリ版と同じ列。メモはダブルクォートで囲み、中の " は "" にする)。 */
fun toCsv(receipts: List<Receipt>): String {
    val header = "日付,金額,カテゴリ,メモ"
    val rows = receipts.map { r -> listOf(r.date, r.amount.toString(), r.category, "\"${r.memo.replace("\"", "\"\"")}\"").joinToString(",") }
    return (listOf(header) + rows).joinToString("\n")
}

/** 一覧の補足行(「2026-09-24 ・ メモ」、メモがなければ日付のみ)。 */
fun Receipt.detailLabel(): String = date + if (memo.isNotEmpty()) " ・ $memo" else ""
