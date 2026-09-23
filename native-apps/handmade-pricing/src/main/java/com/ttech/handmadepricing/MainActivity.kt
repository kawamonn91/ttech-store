package com.ttech.handmadepricing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.ttech.common.theme.TtechTheme
import com.ttech.handmadepricing.ui.HandmadePricingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme(seed = Color(0xFF92400E)) {
                HandmadePricingScreen()
            }
        }
    }
}
