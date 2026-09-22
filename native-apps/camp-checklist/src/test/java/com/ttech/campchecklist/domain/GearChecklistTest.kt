package com.ttech.campchecklist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GearChecklistTest {
    private fun item(name: String, packed: Boolean = false) = GearItem(id = name, name = name, packed = packed)

    @Test
    fun `準備できた数と全体数を数える`() {
        val items = listOf(item("テント", packed = true), item("寝袋", packed = true), item("マット"))
        assertEquals(2 to 3, packedProgress(items))
    }

    @Test
    fun `空リストは0分の0になる`() {
        assertEquals(0 to 0, packedProgress(emptyList()))
    }

    @Test
    fun `該当IDのpacked状態だけ反転する`() {
        val items = listOf(item("テント"), item("寝袋", packed = true))
        val toggled = toggleItem(items, "テント")
        assertTrue(toggled.first { it.id == "テント" }.packed)
        assertTrue(toggled.first { it.id == "寝袋" }.packed)
    }

    @Test
    fun `指定IDの道具を削除する`() {
        val items = listOf(item("テント"), item("寝袋"))
        assertEquals(listOf(item("寝袋")), removeItem(items, "テント"))
    }

    @Test
    fun `全件のpackedをfalseに戻すが道具自体は消えない`() {
        val items = listOf(item("テント", packed = true), item("寝袋", packed = true))
        val reset = resetAllPacked(items)
        assertEquals(2, reset.size)
        assertFalse(reset.any { it.packed })
    }
}
