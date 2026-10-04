package com.ttech.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
private fun lightScheme(seed: Color): ColorScheme {
    val background = Color(0xFFF8FAFC)
    // 背景(不透明)に重ねた色にする。半透明の色をそのままコンテナ色に使うと、
    // 本来は見えないはずの背後の描画が透けて見えることがあるため
    val secondaryContainer = lerp(background, seed, 0.12f)
    return lightColorScheme(
        primary = seed,
        onPrimary = Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = lerp(seed, Color.Black, 0.45f),
        background = background,
        surface = Color.White,
        surfaceVariant = Color(0xFFF1F5F9),
    )
}

private fun darkScheme(seed: Color): ColorScheme {
    val background = Color(0xFF0F172A)
    // アプリの色(seed)は、白地の上で映えるよう選んだ濃さなので、暗い背景にそのまま使うと
    // 沈んで見えたり、文字(onPrimary)とのコントラストが足りなくなったりする。
    // 白に寄せて明るくすることで、Material 3 のダークテーマが期待する明るいトーンに近づける
    val primary = lerp(seed, Color.White, 0.35f)
    val secondaryContainer = lerp(background, seed, 0.35f)
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF0F172A),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = lerp(seed, Color.White, 0.85f),
        background = background,
        surface = Color(0xFF1E293B),
        surfaceVariant = Color(0xFF334155),
    )
}

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
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // 各アプリの画面の多くは、全体を覆う Surface(背景色)を自分では用意していない。
        // これが無いと、画面の地色は Compose ではなく Activity のテーマ(常に白)のままになり、
        // ダークモードでも白い背景にダークの色のカードが乗る、という不自然な見た目になる
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
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
    // ダークモードでは、暗い背景にそのまま使うと沈んで見える色(seed・accent)を、白に寄せて明るくする
    val secondaryColor = if (dark) lerp(seed, Color.White, 0.35f) else seed
    val scheme = base.copy(
        primaryContainer = tint(seed, if (dark) 0.32f else 0.16f),
        onPrimaryContainer = if (dark) lerp(seed, Color.White, 0.75f) else lerp(seed, Color.Black, 0.55f),
        secondary = secondaryColor,
        onSecondary = if (dark) Color(0xFF0F172A) else Color.White,
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
