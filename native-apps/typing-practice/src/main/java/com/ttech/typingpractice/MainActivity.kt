package com.ttech.typingpractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.ttech.common.theme.TtechTheme
import com.ttech.typingpractice.ui.TypingPracticeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme(seed = Color(0xFF1D4ED8)) {
                TypingPracticeScreen()
            }
        }
    }
}
