package com.ttech.datecalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ttech.common.theme.TtechTheme
import com.ttech.datecalculator.ui.DateCalculatorScreen
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme(seed = Color(0xFFF97316)) {
                DateCalculatorScreen()
            }
        }
    }
}
