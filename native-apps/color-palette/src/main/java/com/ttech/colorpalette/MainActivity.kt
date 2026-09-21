package com.ttech.colorpalette

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.ttech.colorpalette.ui.ColorPaletteScreen
import com.ttech.common.theme.TtechTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme(seed = Color(0xFF8B5CF6)) {
                ColorPaletteScreen()
            }
        }
    }
}
