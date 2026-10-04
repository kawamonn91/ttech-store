package com.ttech.bikenavi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.ttech.bikenavi.domain.Place
import com.ttech.common.theme.TtechTheme
import com.ttech.bikenavi.ui.BikeApp

class MainActivity : ComponentActivity() {
    private val debugPlace = mutableStateOf<Place?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readDebugPlace(intent)
        val container = (application as BikeApplication).container
        setContent {
            TtechTheme(seed = Color(0xFF16A34A)) {
                BikeApp(container, debugPlace)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDebugPlace(intent)
    }

    /** 開発用: adb から `--es dest_name 名前 --ed dest_lat 緯度 --ed dest_lon 経度` で目的地を渡せる(リリース版では無効) */
    private fun readDebugPlace(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null || !intent.hasExtra("dest_lat")) return
        debugPlace.value = Place(
            name = intent.getStringExtra("dest_name") ?: "テスト目的地",
            detail = "開発用",
            lat = intent.getDoubleExtra("dest_lat", 0.0),
            lon = intent.getDoubleExtra("dest_lon", 0.0),
        )
    }
}
