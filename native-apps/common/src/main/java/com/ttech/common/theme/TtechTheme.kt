package com.ttech.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
