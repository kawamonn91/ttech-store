package com.ttech.pocketdev

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttech.pocketdev.drive.Drive
import com.ttech.pocketdev.drive.DriveBackend
import com.ttech.pocketdev.drive.DriveLogin
import com.ttech.pocketdev.drive.Rclone
import com.ttech.pocketdev.drive.RcloneDaemon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 接続先の設定の「Drive」タブ。Google でログインし、開くフォルダを選ぶ。
 * ログインは最初の1回だけ（ログインの情報は rclone.conf に残る）。
 */
@Composable
fun DriveSetupSection(initial: ConnectionTarget?, onSave: (ConnectionTarget) -> Unit) {
    val context = LocalContext.current
    val rclone = remember { Drive.rclone(context) }
    val login by DriveLogin.state.collectAsState()
    var loggedIn by remember { mutableStateOf(rclone.isLoggedIn) }

    LaunchedEffect(login.completed) { loggedIn = rclone.isLoggedIn }
    LaunchedEffect(login.authUrl) {
        val url = login.authUrl ?: return@LaunchedEffect
        DriveLogin.urlOpened()
        openAuthPage(context, url)
    }

    Text(
        "Google ドライブのファイルを、この端末から直接編集します。保存するとすぐ Drive に入ります。\n" +
            "ここではプログラムを実行できません。実行するときは、Pocket Linux で Drive と同期したフォルダを使ってください。",
        style = MaterialTheme.typography.bodyMedium,
    )

    when {
        !rclone.available -> Text(
            "この端末の CPU（${Build.SUPPORTED_ABIS.firstOrNull() ?: "不明"}）では、Google ドライブ直結を使えません。",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )

        !rclone.configured -> Text(
            Rclone.NOT_CONFIGURED,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )

        !loggedIn -> DriveLoginCard(
            running = login.running,
            error = login.error,
            onLogin = {
                DriveLogin.clearError()
                DriveLogin.start(context)
            },
            onCancel = DriveLogin::cancel,
        )

        else -> DriveFolderPicker(
            daemon = remember { Drive.daemon(context) },
            initialRoot = initial?.takeIf { it.type == TargetType.GOOGLE_DRIVE }?.driveRoot.orEmpty(),
            onOpen = { root -> onSave(ConnectionTarget.googleDrive(root)) },
            onLogout = {
                Drive.stopDaemon()
                rclone.logout()
                loggedIn = false
            },
        )
    }
}

@Composable
private fun DriveLoginCard(running: Boolean, error: String?, onLogin: () -> Unit, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("ブラウザで Google アカウントを選び、「許可」を押してください", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "許可が終わったら、ブラウザを閉じて（左上の ×）この画面に戻ってください。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("やめる") }
            } else {
                Text(
                    "初めに Google でログインします。ブラウザが開き、「このアプリは Google で確認されていません」と出たら、" +
                        "「詳細」から「T-tech Pocket に移動」へ進んで許可してください（個人で配布しているアプリのため、この表示が出ます）。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Google でログイン")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DriveFolderPicker(
    daemon: RcloneDaemon,
    initialRoot: String,
    onOpen: (String) -> Unit,
    onLogout: () -> Unit,
) {
    // フォルダの一覧も共有の rclone rcd で取る（「開く」を押したあとの IDE でもそのまま使い回す）
    val drive = remember(daemon) { DriveBackend(daemon, "") }

    var path by remember { mutableStateOf(initialRoot) }
    var folders by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(path, reload) {
        folders = null
        error = null
        try {
            folders = withContext(Dispatchers.IO) {
                daemon.ensureStarted()
                drive.listFolders(path)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "フォルダの一覧を取得できません"
        }
    }

    Text("開くフォルダを選んでください", style = MaterialTheme.typography.titleSmall)

    // 今いるフォルダ（押すとそこへ戻る）
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { path = "" }) { Text("マイドライブ") }
        val parts = path.split('/').filter { it.isNotEmpty() }
        parts.forEachIndexed { index, part ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { path = parts.take(index + 1).joinToString("/") }) { Text(part) }
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            if (path.isNotEmpty()) {
                FolderRow(name = "上のフォルダへ", up = true) { path = DriveBackend.parentOf(path) }
                HorizontalDivider()
            }
            val list = folders
            when {
                error != null -> Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { reload++ }) { Text("やり直す") }
                        if (path.isNotEmpty()) TextButton(onClick = { path = "" }) { Text("マイドライブへ") }
                    }
                }

                list == null -> Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("読み込んでいます...", style = MaterialTheme.typography.bodyMedium)
                }

                list.isEmpty() -> Text(
                    "この中にフォルダはありません",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> list.forEach { name ->
                    FolderRow(name = name) { path = DriveBackend.join(path, name) }
                }
            }
        }
    }

    val label = path.substringAfterLast('/').ifEmpty { "マイドライブ全体" }
    Button(onClick = { onOpen(path) }, enabled = error == null, modifier = Modifier.fillMaxWidth()) {
        Text("「$label」を開く", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
        Text("ログアウト（別の Google アカウントに切り替える）")
    }
}

@Composable
private fun FolderRow(name: String, up: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (up) Icons.Default.ArrowUpward else Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Google の同意画面を開く。Custom Tabs ならアプリと同じタスクで開くので、許可のあと × で戻れる。
 * 使えないブラウザしか無ければ、普通にブラウザで開く。
 */
private fun openAuthPage(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
