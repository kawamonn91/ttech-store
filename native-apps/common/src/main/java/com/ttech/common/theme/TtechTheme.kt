package com.ttech.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * T-tech の全アプリで共通の Material 3 テーマ。
 * アプリごとに1色(seed)だけ変えれば、統一感を保ったまま個性を出せる。
 */
private fun lightScheme(seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    secondaryContainer = seed.copy(alpha = 0.12f),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
)

private fun darkScheme(seed: Color) = darkColorScheme(
    primary = seed,
    onPrimary = Color(0xFF0F172A),
    secondaryContainer = seed.copy(alpha = 0.20f),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155),
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

/** T-tech ブランドの既定色(ストア・ストアアプリと同じ青) */
val TtechBrandSeed = Color(0xFF2563EB)

@Composable
fun TtechTheme(seed: Color = TtechBrandSeed, content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkScheme(seed) else lightScheme(seed)
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/**
 * [TtechTheme] の内側で使う。Material 3 の既定では、主色以外(FAB・ナビゲーションバー・カードの地色・補助色)が
 * 紫がかった既定色のままなので、それらもアプリの色(seed)と、差し色(accent)に寄せる。
 * すでにあるアプリの見た目は変えない(使ったアプリだけに効く)。
 */
@Composable
fun TtechTintedSurfaces(seed: Color, accent: Color, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme
    fun tint(color: Color, alpha: Float) = color.copy(alpha = alpha).compositeOver(base.surface)
    val scheme = base.copy(
        primaryContainer = tint(seed, if (dark) 0.32f else 0.16f),
        onPrimaryContainer = if (dark) lerp(seed, Color.White, 0.75f) else lerp(seed, Color.Black, 0.55f),
        secondary = seed,
        tertiary = if (dark) lerp(accent, Color.White, 0.25f) else accent,
        tertiaryContainer = tint(accent, if (dark) 0.30f else 0.16f),
        onTertiaryContainer = if (dark) lerp(accent, Color.White, 0.8f) else lerp(accent, Color.Black, 0.6f),
        surfaceContainerLowest = base.surface,
        surfaceContainerLow = tint(seed, if (dark) 0.10f else 0.05f),
        surfaceContainer = tint(seed, if (dark) 0.14f else 0.08f),
        surfaceContainerHigh = tint(seed, if (dark) 0.18f else 0.11f),
        surfaceContainerHighest = tint(seed, if (dark) 0.22f else 0.14f),
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}
