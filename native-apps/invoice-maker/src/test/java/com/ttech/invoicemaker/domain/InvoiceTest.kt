package com.ttech.invoicemaker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceTest {
    private val data = InvoiceData(
        docType = "請求書",
        docNumber = "INV-001",
        issueDate = "2026-09-24",
        clientName = "株式会社サンプル",
        issuerName = "山田工房",
        items = listOf(LineItem("1", "デザイン費", 1.0, 50_000.0), LineItem("2", "修正対応", 3.0, 3_333.0)),
        taxPercent = 10.0,
    )

    @Test
    fun `新規作成時は空の項目が1行の請求書`() {
        val empty = emptyInvoice("2026-09-24", "a")
        assertEquals("請求書", empty.docType)
        assertEquals(listOf(LineItem("a", "", 1.0, 0.0)), empty.items)
        assertEquals(10.0, empty.taxPercent, 0.0)
    }

    @Test
    fun `小計と消費税と合計を計算し税は円未満切り捨て`() {
        // 50000 + 9999 = 59999、税 5999.9 → 5999
        assertEquals(Totals(59_999.0, 5_999.0, 65_998.0), data.totals())
    }

    @Test
    fun `項目がなければすべて0`() {
        assertEquals(Totals(0.0, 0.0, 0.0), data.copy(items = emptyList()).totals())
    }

    @Test
    fun `項目の追加・更新・削除`() {
        val added = data.addItem("3")
        assertEquals(listOf("1", "2", "3"), added.items.map { it.id })
        val updated = added.updateItem("3") { it.copy(name = "交通費", unitPrice = 1_200.0) }
        assertEquals("交通費", updated.items[2].name)
        assertEquals(listOf("1", "3"), updated.removeItem("2").items.map { it.id })
    }

    @Test
    fun `数値は3桁区切りで整数なら小数点なし`() {
        assertEquals("65,998", formatNumber(65_998.0))
        assertEquals("1.5", formatNumber(1.5))
    }

    @Test
    fun `印刷用HTMLに宛先・明細・合計が入る`() {
        val html = buildInvoiceHtml(data)
        assertTrue(html.contains("<h1>請求書</h1>"))
        assertTrue(html.contains("株式会社サンプル 様"))
        assertTrue(html.contains("No. INV-001"))
        assertTrue(html.contains("<td>修正対応</td><td class=\"num\">3</td><td class=\"num\">3,333</td><td class=\"num\">9,999</td>"))
        assertTrue(html.contains("<span>合計</span><span>65,998円</span>"))
    }

    @Test
    fun `未入力の宛先・発行者・番号は代わりの表示になり備考は入力時だけ出す`() {
        val html = buildInvoiceHtml(InvoiceData(issueDate = "2026-09-24"))
        assertTrue(html.contains("(宛先未入力) 様"))
        assertTrue(html.contains("(発行者未入力)"))
        assertTrue(html.contains("No. -"))
        assertFalse(html.contains("class=\"notes\""))
    }

    @Test
    fun `入力値のHTMLはエスケープする`() {
        val html = buildInvoiceHtml(data.copy(clientName = "<script>alert(1)</script>", notes = "A & B"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; 様"))
        assertTrue(html.contains("A &amp; B"))
    }
}
