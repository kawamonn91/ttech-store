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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ttech.common.theme.TtechTheme
import com.ttech.pocketdev.ssh.LocalBridgeServer
import com.ttech.pocketdev.ssh.SshClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        mutableIntStateOf(
            when (initial?.type) {
                TargetType.POCKET_LINUX -> 0
                TargetType.CUSTOM_SERVER -> 1
                TargetType.SSH -> 2
                else -> 0
            }
        )
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

    // リモートサーバータブ用
    var host by remember {
        mutableStateOf(if (initial?.type == TargetType.CUSTOM_SERVER) initial.baseUrl else "")
    }
    var token by remember {
        mutableStateOf(if (initial?.type == TargetType.CUSTOM_SERVER) initial.token else "")
    }
    var serverName by remember {
        mutableStateOf(if (initial?.type == TargetType.CUSTOM_SERVER) initial.name else "")
    }

    // SSH タブ用
    var sshHost by remember { mutableStateOf(if (initial?.type == TargetType.SSH) initial.sshHost else "") }
    var sshPort by remember { mutableStateOf(if (initial?.type == TargetType.SSH) initial.sshPort.toString() else "22") }
    var sshUser by remember { mutableStateOf(if (initial?.type == TargetType.SSH) initial.sshUser else "root") }
    var sshAuthMethod by remember {
        mutableStateOf(if (initial?.type == TargetType.SSH) initial.sshAuthMethod else ConnectionTarget.SshAuthMethod.PASSWORD)
    }
    var sshPassword by remember { mutableStateOf(if (initial?.type == TargetType.SSH) initial.sshPassword else "") }
    var sshKey by remember { mutableStateOf(if (initial?.type == TargetType.SSH) initial.sshPrivateKey else "") }
    var sshRoot by remember { mutableStateOf(if (initial?.type == TargetType.SSH) initial.sshRemoteRoot else "") }
    var sshName by remember { mutableStateOf(if (initial?.type == TargetType.SSH) initial.name else "") }

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
                text = { Text("SSH") },
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
                // SSH 接続
                Text(
                    "リモートの Linux サーバーに SSH (SFTP + ターミナル) で直接接続します。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = sshHost,
                        onValueChange = { sshHost = it; error = null },
                        label = { Text("ホスト (例: raspi1)") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = sshPort,
                        onValueChange = { sshPort = it; error = null },
                        label = { Text("ポート") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = sshUser,
                    onValueChange = { sshUser = it; error = null },
                    label = { Text("ユーザー名 (例: pi, root)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = sshAuthMethod == ConnectionTarget.SshAuthMethod.PASSWORD,
                        onClick = { sshAuthMethod = ConnectionTarget.SshAuthMethod.PASSWORD },
                    )
                    Text("パスワード認証")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(
                        selected = sshAuthMethod == ConnectionTarget.SshAuthMethod.KEY,
                        onClick = { sshAuthMethod = ConnectionTarget.SshAuthMethod.KEY },
                    )
                    Text("秘密鍵認証")
                }

                if (sshAuthMethod == ConnectionTarget.SshAuthMethod.PASSWORD) {
                    OutlinedTextField(
                        value = sshPassword,
                        onValueChange = { sshPassword = it; error = null },
                        label = { Text("パスワード") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = sshKey,
                        onValueChange = { sshKey = it; error = null },
                        label = { Text("秘密鍵 (PEM / OpenSSH 形式)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }

                OutlinedTextField(
                    value = sshRoot,
                    onValueChange = { sshRoot = it },
                    label = { Text("基準ディレクトリ (任意 例: /home/pi/code)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = sshName,
                    onValueChange = { sshName = it },
                    label = { Text("表示名 (任意 例: Raspberry Pi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        val port = sshPort.toIntOrNull() ?: 22
                        if (sshHost.isBlank()) {
                            error = "ホスト名を入力してください"
                        } else if (sshAuthMethod == ConnectionTarget.SshAuthMethod.PASSWORD && sshPassword.isBlank()) {
                            error = "パスワードを入力してください"
                        } else if (sshAuthMethod == ConnectionTarget.SshAuthMethod.KEY && sshKey.isBlank()) {
                            error = "秘密鍵を入力してください"
                        } else {
                            onSave(
                                ConnectionTarget.ssh(
                                    host = sshHost,
                                    port = port,
                                    user = sshUser,
                                    authMethod = sshAuthMethod,
                                    password = sshPassword,
                                    privateKey = sshKey,
                                    remoteRoot = sshRoot,
                                    name = sshName,
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("SSH で接続する")
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
                val typeName = when (target.type) {
                    TargetType.POCKET_LINUX -> "Pocket Linux"
                    TargetType.SSH -> "SSH"
                    TargetType.CUSTOM_SERVER -> "リモート"
                    TargetType.GOOGLE_DRIVE -> "Google Drive"
                }
                Text(
                    text = if (target.type == TargetType.SSH) "${target.sshUser}@${target.sshHost}:${target.sshPort} ($typeName)"
                           else "${target.baseUrl} ($typeName)",
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

    // SSH 接続の場合のローカルブリッジサーバー
    var activeBridge by remember { mutableStateOf<Pair<LocalBridgeServer, SshClient>?>(null) }
    var effectiveTarget by remember { mutableStateOf(target) }
    var isStartingSsh by remember { mutableStateOf(target.type == TargetType.SSH) }

    val scope = rememberCoroutineScope()

    // SSH の場合、ローカルブリッジサーバーと SSH セッションを非同期起動
    LaunchedEffect(target) {
        if (target.type == TargetType.SSH) {
            isStartingSsh = true
            loadError = null
            withContext(Dispatchers.IO) {
                try {
                    val client = SshClient(target)
                    client.connect()

                    val server = LocalBridgeServer(
                        port = 0, // OS に空きポートを割り当てさせる
                        assets = context.assets,
                        sshClient = client,
                        token = target.token,
                    )
                    server.start()

                    val localUrl = "http://127.0.0.1:${server.listeningPort}"
                    activeBridge = Pair(server, client)
                    effectiveTarget = target.copy(baseUrl = localUrl)
                    isStartingSsh = false
                } catch (e: Exception) {
                    loadError = "SSH 接続に失敗しました: ${e.message}"
                    isStartingSsh = false
                }
            }
        } else {
            effectiveTarget = target
            isStartingSsh = false
        }
    }

    DisposableEffect(target) {
        onDispose {
            webView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null

            activeBridge?.let { (server, client) ->
                runCatching { server.stop() }
                runCatching { client.close() }
            }
            activeBridge = null
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else askLeave = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isStartingSsh) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("SSH セッションを開始しています...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (loadError == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { factoryContext ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    createWebView(factoryContext, effectiveTarget) { message -> loadError = message }
                        .also {
                            webView = it
                            it.loadUrl(effectiveTarget.terminalUrl)
                        }
                },
                update = { view ->
                    if (view.url == null || !effectiveTarget.isOwnHost(view.url.orEmpty())) {
                        view.loadUrl(effectiveTarget.terminalUrl)
                    }
                }
            )
        }

        loadError?.let { message ->
            ErrorOverlay(
                target = target,
                message = message,
                onRetry = {
                    loadError = null
                    if (target.type == TargetType.SSH) {
                        isStartingSsh = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                activeBridge?.let { (s, c) ->
                                    runCatching { s.stop() }
                                    runCatching { c.close() }
                                }
                                val client = SshClient(target)
                                client.connect()
                                val server = LocalBridgeServer(0, context.assets, client, target.token)
                                server.start()
                                activeBridge = Pair(server, client)
                                effectiveTarget = target.copy(baseUrl = "http://127.0.0.1:${server.listeningPort}")
                                isStartingSsh = false
                            } catch (e: Exception) {
                                loadError = "SSH 接続に失敗しました: ${e.message}"
                                isStartingSsh = false
                            }
                        }
                    } else {
                        webView?.loadUrl(effectiveTarget.terminalUrl)
                    }
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

                val hint = when (target.type) {
                    TargetType.POCKET_LINUX -> "Pocket Linux アプリで「IDE のサーバーを動かす」がオンになっているか確認してください。"
                    TargetType.SSH -> "SSH ホスト、ポート、ユーザー名、認証情報、およびネットワーク接続を確認してください。"
                    else -> "サーバーが起動しているか、同じネットワークにいるかを確認してください。"
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
