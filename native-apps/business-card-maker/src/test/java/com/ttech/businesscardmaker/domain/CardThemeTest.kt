package com.ttech.businesscardmaker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardThemeTest {
    @Test
    fun `テーマは3種類でWebアプリ版と同じ名前を持つ`() {
        assertEquals(listOf("シンプル", "ダーク", "ナチュラル"), CARD_THEMES.map { it.label })
    }

    @Test
    fun `各テーマの背景・文字・アクセント色はすべて異なる`() {
        CARD_THEMES.forEach { theme ->
            assertTrue(theme.background != theme.foreground)
            assertTrue(theme.background != theme.accent)
        }
    }

    @Test
    fun `入力済みならそのまま返す`() {
        assertEquals("山田太郎", cardLine("山田太郎", "お名前"))
    }

    @Test
    fun `空欄ならプレースホルダーを返す`() {
        assertEquals("お名前", cardLine("", "お名前"))
        assertEquals("お名前", cardLine("   ", "お名前"))
    }
}
