package com.ttech.receipttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptTest {
    private fun r(id: String, amount: Long, memo: String = "") = Receipt(id, "2026-09-24", amount, "交通費", memo)

    @Test
    fun `入力値から記録を作る`() {
        assertEquals(
            Receipt("1", "2026-09-24", 1200, "会議費", "打ち合わせ", "1.jpg"),
            buildReceipt("1", "2026-09-24", "1200", "会議費", " 打ち合わせ ", "1.jpg"),
        )
    }

    @Test
    fun `金額が0や空なら記録しない`() {
        assertNull(buildReceipt("1", "2026-09-24", "0", "会議費", "", null))
        assertNull(buildReceipt("1", "2026-09-24", "", "会議費", "", null))
    }

    @Test
    fun `合計金額`() {
        assertEquals(1700L, listOf(r("1", 1200), r("2", 500)).total())
    }

    @Test
    fun `CSVはメモをクォートしダブルクォートをエスケープする`() {
        val csv = toCsv(listOf(r("1", 1200, "タクシー"), r("2", 500, "文具\"A4\"")))
        assertEquals(
            "日付,金額,カテゴリ,メモ\n" +
                "2026-09-24,1200,交通費,\"タクシー\"\n" +
                "2026-09-24,500,交通費,\"文具\"\"A4\"\"\"",
            csv,
        )
    }

    @Test
    fun `記録がなければCSVは見出しだけ`() {
        assertEquals("日付,金額,カテゴリ,メモ", toCsv(emptyList()))
    }

    @Test
    fun `補足行はメモがあるときだけ付ける`() {
        assertEquals("2026-09-24 ・ タクシー", r("1", 1, "タクシー").detailLabel())
        assertEquals("2026-09-24", r("1", 1).detailLabel())
    }
}
