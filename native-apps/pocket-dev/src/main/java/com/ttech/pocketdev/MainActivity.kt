package com.ttech.pocketdev

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ttech.common.theme.TtechTheme

/** pocket-dev のブランド色(ターミナルの緑) */
private val BrandSeed = Color(0xFF22C55E)

/**
 * pocket-dev IDE/ターミナル クライアント。
 * Pocket Linux (同一端末)、外部サーバー、SSH、Google Drive などへの接続に対応。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = ServerSettings(this)

        setContent {
            TtechTheme(seed = BrandSeed) {
                var target by remember { mutableStateOf(settings.load()) }
                var editing by remember { mutableStateOf(settings.load() == null) }
                var savedTargets by remember { mutableStateOf(settings.loadAll()) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    val current = target
                    if (editing || current == null) {
                        SetupScreen(
                            initial = current,
                            savedTargets = savedTargets,
                            canCancel = current != null,
                            onSave = { saved ->
                                settings.save(saved)
                                target = saved
                                savedTargets = settings.loadAll()
                                editing = false
                            },
                            onSelectSaved = { selected ->
                                settings.save(selected)
                                target = selected
                                editing = false
                            },
                            onDeleteSaved = { toDelete ->
                                settings.remove(toDelete.id)
                                savedTargets = settings.loadAll()
                                if (target?.id == toDelete.id) {
                                    target = settings.load()
                                }
                            },
                            onCancel = { editing = false },
                        )
                    } else {
                        ServerScreen(
                            target = current,
                            onOpenSettings = {
                                savedTargets = settings.loadAll()
                                editing = true
                            },
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
    savedTargets: List<ConnectionTarget>,
    canCancel: Boolean,
    onSave: (ConnectionTarget) -> Unit,
    onSelectSaved: (ConnectionTarget) -> Unit,
    onDeleteSaved: (ConnectionTarget) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var selectedTab by remember {
        mutableIntStateOf(if (initial?.type == TargetType.POCKET_LINUX) 0 else 1)
    }

    // Pocket Linux タブ用
    var plPort by remember {
        mutableStateOf(
            if (initial?.type == TargetType.POCKET_LINUX) {
                runCatching { java.net.URI(initial.baseUrl).port }.getOrNull()?.takeIf { it != -1 }?.toString() ?: "8765"
            } else "8765"
        )
    }
    var plToken by remember {
        mutableStateOf(if (initial?.type == TargetType.POCKET_LINUX) initial.token else "")
    }

    // 外部サーバータブ用
    var host by remember {
        mutableStateOf(if (initial?.type == TargetType.CUSTOM_SERVER) initial.baseUrl else "")
    }
    var token by remember {
        mutableStateOf(if (initial?.type == TargetType.CUSTOM_SERVER) initial.token else "")
    }
    var serverName by remember {
        mutableStateOf(if (initial?.type == TargetType.CUSTOM_SERVER) initial.name else "")
    }

    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("接続先の設定", style = MaterialTheme.typography.headlineSmall)

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0; error = null },
                text = { Text("Pocket Linux") },
                icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; error = null },
                text = { Text("リモート") },
                icon = { Icon(Icons.Default.Dns, contentDescription = null) },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2; error = null },
                text = { Text("SSH / 他") },
                icon = { Icon(Icons.Default.Computer, contentDescription = null) },
            )
        }

        when (selectedTab) {
            0 -> {
                // Pocket Linux
                Text(
                    "同じ端末の Pocket Linux 内で動いている IDE サーバーに接続します。\n" +
                        "Pocket Linux の設定画面で「IDE のサーバーを動かす」をオンにし、トークンを入力してください。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = plPort,
                    onValueChange = { plPort = it; error = null },
                    label = { Text("ポート番号 (既定: 8765)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = plToken,
                    onValueChange = { plToken = it; error = null },
                    label = { Text("トークン (Pocket Linux の設定で確認)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { launchOrOpenPocketLinux(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pocket Linux を開く")
                    }

                    Button(
                        onClick = {
                            val port = plPort.toIntOrNull() ?: ConnectionTarget.DEFAULT_PORT
                            if (plToken.isBlank()) {
                                error = "Pocket Linux のトークンを入力してください"
                            } else {
                                onSave(ConnectionTarget.pocketLinux(plToken, port))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("接続する")
                    }
                }
            }

            1 -> {
                // リモートサーバー
                Text(
                    "PC や VPS で動かしている pocket-dev サーバーの場所とトークンを入力してください。",
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

                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("表示名 (任意 例: 自宅PC)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        val baseUrl = ConnectionTarget.normalizeBaseUrl(host)
                        when {
                            baseUrl == null -> error = "サーバーの指定を確認してください"
                            token.isBlank() -> error = "トークンを入力してください"
                            else -> onSave(
                                ConnectionTarget(
                                    baseUrl = baseUrl,
                                    token = token.trim(),
                                    name = serverName.trim(),
                                    type = TargetType.CUSTOM_SERVER,
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("接続する")
                }

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

            2 -> {
                // SSH / Google Drive (近日対応案内)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("SSH 接続 / Google Drive 直結", fontWeight = FontWeight.Bold)
                        Text(
                            "・SSH: SFTP によるファイルツリー・コード編集と、リモート PTY ターミナルに対応予定です。\n" +
                                "・Google Drive: Pocket Linux が無い環境でも、rclone 連携による Drive ファイル直接編集に対応予定です。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (canCancel) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("キャンセル")
            }
        }

        // 保存済み接続先リスト
        if (savedTargets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Text("保存済みの接続先", style = MaterialTheme.typography.titleMedium)

            savedTargets.forEach { saved ->
                SavedTargetItem(
                    target = saved,
                    onSelect = { onSelectSaved(saved) },
                    onDelete = { onDeleteSaved(saved) },
                )
            }
        }
    }
}

@Composable
private fun SavedTargetItem(
    target: ConnectionTarget,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.displayName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${target.baseUrl} (${if (target.type == TargetType.POCKET_LINUX) "Pocket Linux" else "リモート"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Pocket Linux アプリを起動する。未インストールの場合は案内 */
private fun launchOrOpenPocketLinux(context: Context) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(ConnectionTarget.POCKET_LINUX_PACKAGE)
    if (intent != null) {
        context.startActivity(intent)
    } else {
        val storeUrl = "https://store.kawamonn.com/apps/pocket-linux"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)))
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
                target = target,
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
            text = { Text("接続先: ${target.displayName}") },
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
    context: Context,
    target: ConnectionTarget,
    onError: (String) -> Unit,
): WebView = WebView(context).apply {
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
            // 自分のサーバー以外へのリンクは外部ブラウザに渡す
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            return true
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                onError("${target.displayName} に接続できません (${error.description})")
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    target: ConnectionTarget,
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("接続できません", style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodySmall)

                val hint = if (target.type == TargetType.POCKET_LINUX) {
                    "Pocket Linux アプリで「IDE のサーバーを動かす」がオンになっているか確認してください。"
                } else {
                    "サーバーが起動しているか、同じネットワークにいるかを確認してください。"
                }
                Text(hint, style = MaterialTheme.typography.bodySmall)

                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("再試行") }
                TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("接続先を変更")
                }
            }
        }
    }
}
