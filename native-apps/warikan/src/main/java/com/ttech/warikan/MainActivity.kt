package com.ttech.warikan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ttech.common.theme.TtechTheme
import com.ttech.warikan.ui.WarikanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme {
                WarikanScreen()
            }
        }
    }
}
