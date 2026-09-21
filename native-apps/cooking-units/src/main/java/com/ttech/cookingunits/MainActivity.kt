package com.ttech.cookingunits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.ttech.common.theme.TtechTheme
import com.ttech.cookingunits.ui.CookingUnitsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme(seed = Color(0xFFEC4899)) {
                CookingUnitsScreen()
            }
        }
    }
}
