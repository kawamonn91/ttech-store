package com.kawamonn.store

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.kawamonn.store.ui.LocalContainer
import com.kawamonn.store.ui.StoreNavHost
import com.kawamonn.store.ui.StoreTheme

class MainActivity : ComponentActivity() {
    /** 外部(Webの詳細ページ・通知)から開かれたときの遷移先。消費したら null に戻す */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ステータスバー・ナビゲーションバーの文字色を、画面の明暗(ライト/ダーク)に合わせる(白地に白で読めなくなるのを防ぐ)
        enableEdgeToEdge()
        pendingRoute = routeFrom(intent)
        val container = (application as StoreApp).container

        setContent {
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            CompositionLocalProvider(LocalContainer provides container) {
                StoreTheme {
                    StoreNavHost(
                        pendingRoute = pendingRoute,
                        onRouteConsumed = { pendingRoute = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeFrom(intent)
    }

    private fun routeFrom(intent: Intent?): String? {
        intent ?: return null
        intent.getStringExtra(EXTRA_ROUTE)?.let { return it }
        val data = intent.data ?: return null
        // https://store.kawamonn.com/apps/<slug> または ttechstore://apps/<slug>
        val slug = when (data.scheme) {
            "https" -> data.pathSegments.getOrNull(1)?.takeIf { data.pathSegments.firstOrNull() == "apps" }
            "ttechstore" -> data.pathSegments.firstOrNull()
            else -> null
        }
        return slug?.let { "detail/$it" }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
