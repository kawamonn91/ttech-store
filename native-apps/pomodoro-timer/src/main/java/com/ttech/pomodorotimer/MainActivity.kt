package com.ttech.pomodorotimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ttech.common.theme.TtechTheme
import com.ttech.pomodorotimer.ui.PomodoroScreen
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme(seed = Color(0xFFD93025)) {
                PomodoroScreen()
            }
        }
    }
}
