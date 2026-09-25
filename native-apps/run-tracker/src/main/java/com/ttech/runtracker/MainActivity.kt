package com.ttech.runtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.ttech.common.theme.TtechTheme
import com.ttech.runtracker.ui.RunApp

class MainActivity : ComponentActivity() {
    private val openRunId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRunId.value = intent?.getStringExtra(EXTRA_RUN_ID)
        val container = (application as RunApplication).container
        setContent {
            TtechTheme(seed = Color(0xFFFC4C02)) {
                RunApp(container, openRunId)
            }
        }
    }

    /** 通知(「ランを記録しました」)から開かれたとき、その記録の詳細を出す */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRunId.value = intent.getStringExtra(EXTRA_RUN_ID)
    }

    companion object {
        const val EXTRA_RUN_ID = "run_id"
    }
}
