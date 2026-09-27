package com.ttech.ideamemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import com.ttech.common.theme.TtechTheme
import com.ttech.common.theme.TtechTintedSurfaces
import com.ttech.ideamemo.ui.IdeaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ステータスバー・ナビゲーションバーの文字色を、画面の明暗(ライト/ダーク)に合わせる
        enableEdgeToEdge()
        val repository = (application as IdeaApplication).repository
        setContent {
            val seed = Color(0xFF6D28D9)
            TtechTheme(seed = seed) {
                TtechTintedSurfaces(seed = seed, accent = Color(0xFFFBBF24)) {
                    IdeaApp(repository)
                }
            }
        }
    }
}
