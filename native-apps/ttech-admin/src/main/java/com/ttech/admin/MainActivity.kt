package com.ttech.admin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import com.ttech.admin.ui.AdminApp
import com.ttech.common.theme.TtechTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // ステータスバーの文字が背景に溶けないように、明るさに合わせて自動で切り替える
        // 利用者の個人情報を表示するので、リリース版ではスクリーンショット・画面録画・最近使ったアプリの画面への表示を防ぐ
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val container = (application as AdminApplication).container
        setContent {
            TtechTheme(seed = Color(0xFF4F46E5)) {
                AdminApp(container)
            }
        }
    }
}
