package com.kawamonn.store.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kawamonn.store.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
)

@Composable
fun StoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
