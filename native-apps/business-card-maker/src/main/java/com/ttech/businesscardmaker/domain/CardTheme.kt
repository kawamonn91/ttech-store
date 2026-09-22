package com.ttech.businesscardmaker.domain

/** 名刺のカラーテーマ(背景・文字・アクセント)。色はARGBのLong値(0xAARRGGBB)。 */
data class CardTheme(val label: String, val background: Long, val foreground: Long, val accent: Long)

/** Webアプリ版の3テーマをそのまま踏襲。 */
val CARD_THEMES = listOf(
    CardTheme(label = "シンプル", background = 0xFFFFFFFF, foreground = 0xFF0F172A, accent = 0xFF2563EB),
    CardTheme(label = "ダーク", background = 0xFF0F172A, foreground = 0xFFF1F5F9, accent = 0xFF60A5FA),
    CardTheme(label = "ナチュラル", background = 0xFFFAF7F2, foreground = 0xFF3F3A34, accent = 0xFFA16207),
)

/** 空欄のまま共有すると読みにくいので、空ならプレースホルダーに差し替える。 */
fun cardLine(text: String, placeholder: String): String = text.ifBlank { placeholder }
