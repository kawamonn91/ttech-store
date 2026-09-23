package com.ttech.goshuincho.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoshuinEntryTest {
    private fun e(id: String, prefecture: String) = GoshuinEntry(id, "神社$id", prefecture, "2026-09-23", "")

    @Test
    fun `神社寺院名があれば有効`() {
        assertTrue(isValidEntry("明治神宮"))
    }

    @Test
    fun `神社寺院名が空または空白のみなら無効`() {
        assertFalse(isValidEntry(""))
        assertFalse(isValidEntry("   "))
    }

    @Test
    fun `都道府県別に参拝数を数える`() {
        val entries = listOf(e("1", "東京都"), e("2", "東京都"), e("3", "京都府"))
        val counts = entries.countByPrefecture()
        assertEquals(2, counts.first { it.prefecture == "東京都" }.count)
        assertEquals(1, counts.first { it.prefecture == "京都府" }.count)
    }

    @Test
    fun `都道府県未入力の記録は内訳に含めない`() {
        val entries = listOf(e("1", "東京都"), e("2", ""))
        val counts = entries.countByPrefecture()
        assertEquals(1, counts.size)
    }

    @Test
    fun `内訳は初めて登場した都道府県の順に並ぶ`() {
        val entries = listOf(e("1", "京都府"), e("2", "東京都"), e("3", "京都府"), e("4", "大阪府"))
        val counts = entries.countByPrefecture()
        assertEquals(listOf("京都府", "東京都", "大阪府"), counts.map { it.prefecture })
    }

    @Test
    fun `記録がなければ内訳は空`() {
        assertEquals(emptyList<PrefectureCount>(), emptyList<GoshuinEntry>().countByPrefecture())
    }
}
