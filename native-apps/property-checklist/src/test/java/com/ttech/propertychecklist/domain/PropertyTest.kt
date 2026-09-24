package com.ttech.propertychecklist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyTest {
    private fun ids(): () -> String {
        var n = 0
        return { "id${n++}" }
    }

    @Test
    fun `新しい物件は初期の10項目が未チェックで入る`() {
        val p = newProperty("渋谷区1-2-3", "2026-09-24", ids())
        assertEquals("id0", p.id)
        assertEquals(DEFAULT_LABELS, p.items.map { it.label })
        assertTrue(p.items.none { it.checked })
        assertEquals(p.items.size, p.items.map { it.id }.toSet().size)
    }

    @Test
    fun `指定した物件の指定項目だけチェックを反転する`() {
        val a = newProperty("A", "2026-09-24", ids())
        val b = a.copy(id = "other")
        val target = a.items[2].id
        val next = listOf(a, b).toggleItem(a.id, target)
        assertTrue(next[0].items[2].checked)
        assertEquals(1, next[0].items.count { it.checked })
        assertFalse(next[1].items.any { it.checked })
        assertFalse(next.toggleItem(a.id, target)[0].items[2].checked)
    }

    @Test
    fun `メモは指定した物件だけ更新する`() {
        val a = newProperty("A", "2026-09-24", ids())
        val b = a.copy(id = "other")
        val next = listOf(a, b).updateNotes(a.id, "南向きで明るい")
        assertEquals("南向きで明るい", next[0].notes)
        assertEquals("", next[1].notes)
    }

    @Test
    fun `一覧ラベルにチェック数を出す`() {
        val p = newProperty("A", "2026-09-24", ids())
        assertEquals("A(0/10)", p.progressLabel())
        assertEquals("A(1/10)", listOf(p).toggleItem(p.id, p.items[0].id)[0].progressLabel())
    }
}
