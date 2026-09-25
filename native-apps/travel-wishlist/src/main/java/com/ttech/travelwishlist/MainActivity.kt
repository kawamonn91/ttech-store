package com.ttech.travelwishlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import com.ttech.common.theme.TtechTheme
import com.ttech.common.theme.TtechTintedSurfaces
import com.ttech.travelwishlist.ui.WishApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ステータスバー・ナビゲーションバーの文字色を、画面の明暗(ライト/ダーク)に合わせる
        enableEdgeToEdge()
        val repository = (application as WishApplication).repository
        setContent {
            val seed = Color(0xFFDB2777)
            TtechTheme(seed = seed) {
                TtechTintedSurfaces(seed = seed, accent = Color(0xFF0E7490)) {
                    WishApp(repository)
                }
            }
        }
    }
}
