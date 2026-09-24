package com.ttech.restaurantchecklist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockItemTest {
    private fun item(id: String, target: Double, current: Double) = StockItem(id, "品目$id", target, current, "個")

    @Test
    fun `入力値から品目を作り現在量は0から始める`() {
        assertEquals(StockItem("1", "玉ねぎ", 10.0, 0.0, "個"), buildStockItem("1", " 玉ねぎ ", "10", "個"))
        assertEquals(1.5, buildStockItem("1", "だし", "1.5", "L")!!.targetQty, 0.0)
    }

    @Test
    fun `品目名が空や必要量が空・0なら作らない`() {
        assertNull(buildStockItem("1", " ", "10", "個"))
        assertNull(buildStockItem("1", "玉ねぎ", "", "個"))
        assertNull(buildStockItem("1", "玉ねぎ", "0", "個"))
    }

    @Test
    fun `品目は登録順に並ぶ`() {
        val items = emptyList<StockItem>().addItem(item("1", 1.0, 0.0)).addItem(item("2", 1.0, 0.0))
        assertEquals(listOf("1", "2"), items.map { it.id })
    }

    @Test
    fun `現在量を更新しマイナスは0にする`() {
        val items = listOf(item("1", 10.0, 0.0), item("2", 5.0, 5.0))
        assertEquals(4.0, items.updateQty("1", 4.0)[0].currentQty, 0.0)
        assertEquals(0.0, items.updateQty("1", -3.0)[0].currentQty, 0.0)
        assertEquals(5.0, items.updateQty("1", 4.0)[1].currentQty, 0.0)
    }

    @Test
    fun `現在量が必要量に足りない品目が仕込み対象`() {
        val items = listOf(item("1", 10.0, 3.0), item("2", 5.0, 5.0), item("3", 2.0, 4.0))
        assertEquals(listOf("1"), items.needsPrep().map { it.id })
    }

    @Test
    fun `数量は整数なら小数点を付けない`() {
        assertEquals("3", formatQty(3.0))
        assertEquals("1.5", formatQty(1.5))
    }
}
