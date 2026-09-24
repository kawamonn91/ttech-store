package com.ttech.invoicemaker.domain

import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor

/** 文書の種類(Webアプリ版と同じ2種)。 */
val DOC_TYPES = listOf("見積書", "請求書")

@Serializable
data class LineItem(
    val id: String,
    val name: String = "",
    val qty: Double = 1.0,
    val unitPrice: Double = 0.0,
) {
    val amount: Double get() = qty * unitPrice
}

@Serializable
data class InvoiceData(
    /** "見積書" または "請求書" */
    val docType: String = "請求書",
    val docNumber: String = "",
    /** 発行日 ISO8601 (YYYY-MM-DD) */
    val issueDate: String = "",
    val clientName: String = "",
    val issuerName: String = "",
    val items: List<LineItem> = emptyList(),
    val taxPercent: Double = 10.0,
    val notes: String = "",
)

/** 新規作成時の初期状態(Webアプリ版と同じく、空の項目が1行・税率10%の請求書)。 */
fun emptyInvoice(issueDate: String, firstItemId: String): InvoiceData =
    InvoiceData(issueDate = issueDate, items = listOf(LineItem(id = firstItemId)))

data class Totals(val subtotal: Double, val tax: Double, val total: Double)

/** 小計・消費税(円未満切り捨て)・合計。Webアプリ版と同じ計算。 */
fun InvoiceData.totals(): Totals {
    val subtotal = items.sumOf { it.amount }
    val tax = floor(subtotal * taxPercent / 100)
    return Totals(subtotal, tax, subtotal + tax)
}

fun InvoiceData.updateItem(id: String, transform: (LineItem) -> LineItem): InvoiceData =
    copy(items = items.map { if (it.id == id) transform(it) else it })

fun InvoiceData.addItem(id: String): InvoiceData = copy(items = items + LineItem(id = id))

fun InvoiceData.removeItem(id: String): InvoiceData = copy(items = items.filterNot { it.id == id })

/** JSの toLocaleString() と同じく3桁区切り・小数は3桁まで(整数なら小数点なし)。 */
fun formatNumber(value: Double): String =
    NumberFormat.getNumberInstance(Locale.JAPAN).apply { maximumFractionDigits = 3 }.format(value)

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** 印刷・PDF保存用のHTML(Webアプリ版の印刷プレビューと同じ構成)。入力値はすべてエスケープする。 */
fun buildInvoiceHtml(data: InvoiceData): String {
    val t = data.totals()
    val rows = data.items.joinToString("") { i ->
        "<tr><td>${i.name.escapeHtml()}</td><td class=\"num\">${formatNumber(i.qty)}</td>" +
            "<td class=\"num\">${formatNumber(i.unitPrice)}</td><td class=\"num\">${formatNumber(i.amount)}</td></tr>"
    }
    val notes = if (data.notes.isNotEmpty()) "<p class=\"notes\">${data.notes.escapeHtml()}</p>" else ""
    return """
        <!DOCTYPE html>
        <html lang="ja"><head><meta charset="utf-8"><title>${data.docType.escapeHtml()}</title>
        <style>
        body { font-family: sans-serif; color: #111; margin: 24px; font-size: 13px; }
        h1 { text-align: center; font-size: 22px; margin: 0 0 16px; }
        .head { display: flex; justify-content: space-between; }
        .right { text-align: right; }
        table { width: 100%; border-collapse: collapse; margin-top: 24px; }
        th { text-align: left; color: #555; border-bottom: 1px solid #ccc; padding: 4px 0; }
        td { border-bottom: 1px solid #ccc; padding: 4px 0; }
        .num { text-align: right; }
        .totals { width: 220px; margin: 16px 0 0 auto; }
        .totals div { display: flex; justify-content: space-between; padding: 2px 0; }
        .totals .total { border-top: 1px solid #ccc; font-weight: bold; }
        .notes { margin-top: 24px; white-space: pre-wrap; color: #555; font-size: 11px; }
        </style></head><body>
        <h1>${data.docType.escapeHtml()}</h1>
        <div class="head">
        <div><p>${data.clientName.ifEmpty { "(宛先未入力)" }.escapeHtml()} 様</p></div>
        <div class="right"><p>No. ${data.docNumber.ifEmpty { "-" }.escapeHtml()}</p><p>${data.issueDate.escapeHtml()}</p>
        <p>${data.issuerName.ifEmpty { "(発行者未入力)" }.escapeHtml()}</p></div>
        </div>
        <table><thead><tr><th>品目</th><th class="num">数量</th><th class="num">単価</th><th class="num">金額</th></tr></thead>
        <tbody>$rows</tbody></table>
        <div class="totals">
        <div><span>小計</span><span>${formatNumber(t.subtotal)}円</span></div>
        <div><span>消費税(${formatNumber(data.taxPercent)}%)</span><span>${formatNumber(t.tax)}円</span></div>
        <div class="total"><span>合計</span><span>${formatNumber(t.total)}円</span></div>
        </div>
        $notes
        </body></html>
    """.trimIndent()
}
