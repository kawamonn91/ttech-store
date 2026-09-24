package com.ttech.driverecord

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.ttech.common.theme.TtechTheme
import com.ttech.driverecord.ui.DriveApp

class MainActivity : ComponentActivity() {
    private val openDriveId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openDriveId.value = intent?.getStringExtra(EXTRA_DRIVE_ID)
        val container = (application as DriveApplication).container
        setContent {
            TtechTheme(seed = Color(0xFFE11D48)) {
                DriveApp(container, openDriveId)
            }
        }
    }

    /** 通知(「ドライブを記録しました」)から開かれたとき、その記録の詳細を出す */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDriveId.value = intent.getStringExtra(EXTRA_DRIVE_ID)
    }

    companion object {
        const val EXTRA_DRIVE_ID = "drive_id"
    }
}
