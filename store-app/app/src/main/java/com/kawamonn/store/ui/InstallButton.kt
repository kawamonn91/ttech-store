package com.kawamonn.store.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kawamonn.store.data.api.LatestReleaseDto
import com.kawamonn.store.install.InstallState

/** アプリの状態(未導入/更新あり/最新/進行中)に応じた操作ボタン。詳細画面とアップデート画面で共通 */
@Composable
fun InstallButton(packageName: String, latest: LatestReleaseDto?, modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val states by container.installController.states.collectAsState()
    val tick by container.installController.installedTick.collectAsState()

    var installed by remember(packageName) { mutableStateOf<Long?>(null) }
    LifecycleResumeEffect(packageName, tick) {
        installed = container.installedApps.versionCode(packageName)
        onPauseOrDispose {}
    }

    var askPermission by remember { mutableStateOf(false) }
    val state = states[packageName]

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            latest == null -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("準備中") }

            latest.minSdk != null && latest.minSdk > Build.VERSION.SDK_INT ->
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("この端末には非対応です") }

            state is InstallState.Downloading -> {
                val total = state.total
                if (total != null && total > 0) {
                    LinearProgressIndicator(progress = { state.downloaded.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ダウンロード中 ${(state.downloaded * 100 / total)}%", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { container.installController.cancel(packageName) }) { Text("キャンセル") }
                    }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("準備中…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            state is InstallState.Verifying -> Busy("ファイルを検証中…")
            state is InstallState.Installing -> Busy("インストール中…")
            state is InstallState.AwaitingUser -> Busy("確認画面で操作してください")

            else -> {
                if (state is InstallState.Failed) {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                val current = installed
                val startInstall = {
                    if (!container.installController.canRequestInstall()) {
                        askPermission = true
                    } else {
                        container.installController.install(packageName, latest.releaseId)
                    }
                }
                when {
                    current == null -> Button(onClick = { startInstall() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state is InstallState.Failed) "もう一度試す" else "インストール")
                    }
                    current < latest.versionCode -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { startInstall() }, modifier = Modifier.weight(1f)) { Text("更新") }
                        OpenButton(packageName, Modifier.weight(1f))
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OpenButton(packageName, Modifier.weight(1f), primary = true)
                    }
                }
            }
        }
    }

    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text("インストールの許可が必要です") },
            text = { Text("アプリをインストールするため、T-tech Store に「このアプリからのインストール」を許可してください。設定画面で許可したあと、戻ってもう一度お試しください。") },
            confirmButton = {
                TextButton(onClick = {
                    askPermission = false
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("設定を開く") }
            },
            dismissButton = { TextButton(onClick = { askPermission = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun Busy(text: String) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun OpenButton(packageName: String, modifier: Modifier = Modifier, primary: Boolean = false) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val launch = container.installedApps.launchIntent(packageName)
    val onClick = { launch?.let { context.startActivity(it) } ?: Unit }
    if (primary) {
        Button(onClick = onClick, enabled = launch != null, modifier = modifier.fillMaxWidth()) { Text("開く") }
    } else {
        OutlinedButton(onClick = onClick, enabled = launch != null, modifier = modifier) { Text("開く") }
    }
}
