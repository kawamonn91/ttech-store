package com.ttech.babylog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.ttech.babylog.ui.BabyLogScreen
import com.ttech.common.theme.TtechTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme(seed = Color(0xFFFBBF24)) {
                BabyLogScreen()
            }
        }
    }
}
