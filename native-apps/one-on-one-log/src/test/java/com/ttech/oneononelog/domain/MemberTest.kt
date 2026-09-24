package com.ttech.oneononelog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MemberTest {
    private val record = OneOnOneRecord("r1", "2026-09-24", "良い", "", "次")

    @Test
    fun `メンバーは追加順に並ぶ`() {
        val members = emptyList<Member>().addMember(Member("1", "山田")).addMember(Member("2", "佐藤"))
        assertEquals(listOf("山田", "佐藤"), members.map { it.name })
    }

    @Test
    fun `メンバーを削除する`() {
        val members = listOf(Member("1", "山田"), Member("2", "佐藤"))
        assertEquals(listOf("2"), members.removeMember("1").map { it.id })
    }

    @Test
    fun `記録は指定メンバーの先頭に入り他のメンバーには入らない`() {
        val older = record.copy(id = "r0")
        val members = listOf(Member("1", "山田", listOf(older)), Member("2", "佐藤"))
        val next = members.addRecord("1", record)
        assertEquals(listOf("r1", "r0"), next[0].records.map { it.id })
        assertEquals(emptyList<OneOnOneRecord>(), next[1].records)
    }

    @Test
    fun `記録の各項目は前後の空白を除く`() {
        assertEquals(
            OneOnOneRecord("r", "2026-09-24", "a", "b", "c"),
            buildRecord("r", "2026-09-24", " a ", "b\n", " c"),
        )
    }

    @Test
    fun `表示行は空の項目を出さない`() {
        assertEquals(listOf("良かったこと: 良い", "次のアクション: 次"), record.displayLines())
    }
}
