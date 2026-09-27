package com.ttech.weightlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import com.ttech.common.theme.TtechTheme
import com.ttech.common.theme.TtechTintedSurfaces
import com.ttech.weightlog.ui.WeightApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ステータスバー・ナビゲーションバーの文字色を、画面の明暗(ライト/ダーク)に合わせる
        enableEdgeToEdge()
        val repository = (application as WeightApplication).repository
        setContent {
            val seed = Color(0xFFF59E0B)
            TtechTheme(seed = seed) {
                TtechTintedSurfaces(seed = seed, accent = Color(0xFF16A34A)) {
                    WeightApp(repository)
                }
            }
        }
    }
}
