package com.ttech.pocketdev

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ttech.common.theme.TtechTheme

/** pocket-dev のブランド色(ターミナルの緑) */
private val BrandSeed = Color(0xFF22C55E)

/**
 * 自分で立てた pocket-dev サーバーに繋ぐクライアント。
 * 画面は WebView で、ターミナルとIDEはサーバーが配信するページをそのまま表示する。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = ServerSettings(this)

        setContent {
            TtechTheme(seed = BrandSeed) {
                var target by remember { mutableStateOf(settings.load()) }
                var editing by remember { mutableStateOf(settings.load() == null) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // targetSdk 35 以降は画面端まで描画されるため、
                        // システムバーとソフトキーボードの領域を自分で避ける
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    val current = target
                    if (editing || current == null) {
                        SetupScreen(
                            initial = current,
                            canCancel = current != null,
                            onSave = { saved ->
                                settings.save(saved)
                                target = saved
                                editing = false
                            },
                            onCancel = { editing = false },
                        )
                    } else {
                        ServerScreen(
                            target = current,
                            onOpenSettings = { editing = true },
                            onExit = { finish() },
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 設定画面 */

@Composable
private fun SetupScreen(
    initial: ConnectionTarget?,
    canCancel: Boolean,
    onSave: (ConnectionTarget) -> Unit,
    onCancel: () -> Unit,
) {
    var host by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var token by remember { mutableStateOf(initial?.token ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("接続先の設定", style = MaterialTheme.typography.headlineSmall)
        Text(
            "PC や VPS で動かしている pocket-dev サーバーの場所と、" +
                "起動時に表示されたトークンを入力してください。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it; error = null },
            label = { Text("サーバー (例: 192.168.1.10:8765)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it; error = null },
            label = { Text("トークン") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                val baseUrl = ConnectionTarget.normalizeBaseUrl(host)
                when {
                    baseUrl == null -> error = "サーバーの指定を確認してください"
                    token.isBlank() -> error = "トークンを入力してください"
                    else -> onSave(ConnectionTarget(baseUrl, token.trim()))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("接続する")
        }

        if (canCancel) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("キャンセル")
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                "サーバーの起動方法:\n" +
                    "PC で docker compose up -d を実行し、表示された\n" +
                    "http://<PCのIP>:8765/?token=... の IP とトークンを入れてください。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

/* ------------------------------------------------------------- WebView 画面 */

@Composable
private fun ServerScreen(
    target: ConnectionTarget,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var askLeave by remember { mutableStateOf(false) }

    // 戻るキー: ページを戻れるなら戻り、戻れないなら終了か設定を選ばせる
    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else askLeave = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { factoryContext ->
                CookieManager.getInstance().setAcceptCookie(true)
                createWebView(factoryContext, target) { message -> loadError = message }
                    .also {
                        webView = it
                        it.loadUrl(target.terminalUrl)
                    }
            },
        )

        loadError?.let { message ->
            ErrorOverlay(
                message = message,
                onRetry = {
                    loadError = null
                    webView?.loadUrl(target.terminalUrl)
                },
                onOpenSettings = onOpenSettings,
            )
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("pocket-dev") },
            text = { Text("接続先: ${target.baseUrl}") },
            confirmButton = {
                TextButton(onClick = { askLeave = false; onOpenSettings() }) { Text("設定") }
            },
            dismissButton = {
                TextButton(onClick = { askLeave = false; onExit() }) { Text("終了") }
            },
        )
    }

    // 画面を離れるときに WebView を破棄して、PTY の接続を残さない
    androidx.compose.runtime.DisposableEffect(target) {
        onDispose {
            webView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    target: ConnectionTarget,
    onError: (String) -> Unit,
): WebView = WebView(context).apply {
    // ターミナルとエディタは JavaScript と localStorage で動くため、どちらも必須
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.setSupportZoom(false)
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    settings.mediaPlaybackRequiresUserGesture = false
    setBackgroundColor(android.graphics.Color.parseColor("#0F1117"))

    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (target.isOwnHost(url)) return false
            // 自分のサーバー以外へのリンクは、アプリ内で開かず外部ブラウザに渡す
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            return true
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                onError("${target.baseUrl} に接続できません (${error.description})")
            }
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("接続できません", style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodySmall)
                Text(
                    "サーバーが起動しているか、同じネットワークにいるかを確認してください。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("再試行") }
                TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("接続先を変更")
                }
            }
        }
    }
}
