package com.kawamonn.store.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kawamonn.store.auth.AuthState
import com.kawamonn.store.auth.TotpRequiredException
import com.kawamonn.store.ui.LocalContainer
import com.kawamonn.store.ui.SectionTitle
import com.kawamonn.store.ui.auth.LoginScreen
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.kawamonn.store.ui.openStoreWebPage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull

/** [releaseState] は、最新のリリースの状態(「v1.0.0 公開中(自動審査を通過)」など) */
private data class MyApp(val name: String, val status: String, val releaseState: String?)

/** [reasons] は、運営の確認が必要とされた理由(自動審査の結果)。運営が中身を確かめてから公開・却下を選べるように出す */
private data class PendingRelease(
    val id: String,
    val appName: String,
    val developerName: String?,
    val versionName: String?,
    val status: String,
    val reasons: List<String>,
)

/** 埋め込み(apps や developers)が、オブジェクトでも1要素の配列でも読めるようにする */
private fun JsonElement?.asObject(): JsonObject? = when (this) {
    is JsonObject -> this
    is JsonArray -> firstOrNull() as? JsonObject
    else -> null
}

@Composable
fun AccountScreen(contentPadding: PaddingValues) {
    val container = LocalContainer.current
    val authState by container.authRepository.state.collectAsState()

    when (val state = authState) {
        is AuthState.SignedOut -> LoginScreen(container.authRepository) {}
        is AuthState.SignedIn -> SignedInAccount(state, contentPadding)
    }
}

@Composable
private fun SignedInAccount(state: AuthState.SignedIn, contentPadding: PaddingValues) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()

    var role by remember { mutableStateOf<String?>(null) }
    var myApps by remember { mutableStateOf<List<MyApp>>(emptyList()) }
    var pending by remember { mutableStateOf<List<PendingRelease>>(emptyList()) }
    var privateApps by remember { mutableStateOf<List<com.kawamonn.store.PrivateApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    // 2段階認証(TOTP)が必要と言われたときの入力ダイアログ用。成功したら pendingRetry の操作をやり直す
    var totpPrompt by remember { mutableStateOf(false) }
    var pendingRetry by remember { mutableStateOf<Pair<String, String>?>(null) }
    var totpCode by remember { mutableStateOf("") }
    var totpError by remember { mutableStateOf<String?>(null) }
    var totpBusy by remember { mutableStateOf(false) }

    LaunchedEffect(state.userId, reloadTick) {
        val postgrest = container.postgrest
        runCatching {
            val profileRows = postgrest.select("profiles", "id=eq.${state.userId}&select=role")
            role = profileRows.firstOrNull()?.jsonObject?.get("role")?.jsonPrimitive?.contentOrNull

            val appRows = postgrest.select(
                "apps",
                "developer_id=eq.${state.userId}&select=name,status,app_releases(version_name,status,auto_approved,policy_verdict,created_at)&order=created_at.desc",
            )
            myApps = appRows.map { row ->
                val obj = row.jsonObject
                val releases = (obj["app_releases"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                val newest = newestRelease(releases) { it["created_at"]?.jsonPrimitive?.contentOrNull ?: "" }
                MyApp(
                    name = obj["name"]!!.jsonPrimitive.content,
                    status = obj["status"]!!.jsonPrimitive.content,
                    releaseState = newest?.let { r ->
                        val version = r["version_name"]?.jsonPrimitive?.contentOrNull?.let { "v$it " } ?: ""
                        version + releaseStateLabel(
                            status = r["status"]?.jsonPrimitive?.contentOrNull ?: "",
                            autoApproved = r["auto_approved"]?.jsonPrimitive?.contentOrNull == "true",
                            verdict = r["policy_verdict"]?.jsonPrimitive?.contentOrNull,
                        )
                    },
                )
            }

            if (role == "admin") {
                val releaseRows = postgrest.select(
                    "app_releases",
                    "status=in.(scanned,approved)&select=id,version_name,status,policy_findings,apps(name,developers(name))&order=created_at.desc",
                )
                pending = releaseRows.map {
                    val obj = it.jsonObject
                    val app = obj["apps"].asObject()
                    PendingRelease(
                        id = obj["id"]!!.jsonPrimitive.content,
                        appName = app?.get("name")?.jsonPrimitive?.contentOrNull ?: "?",
                        developerName = app?.get("developers").asObject()?.get("name")?.jsonPrimitive?.contentOrNull,
                        versionName = obj["version_name"]?.jsonPrimitive?.contentOrNull,
                        status = obj["status"]!!.jsonPrimitive.content,
                        reasons = reviewReasons(obj["policy_findings"]),
                    )
                }
            } else {
                pending = emptyList()
            }
        }.onFailure { error = it.message }

        // 管理者専用アプリ(公開カタログに出ないもの)。他の読み込みが失敗しても、ここは独立して試す
        privateApps = if (role == "admin") runCatching { container.adminPrivateApps() }.getOrDefault(emptyList()) else emptyList()
    }

    fun decide(releaseId: String, action: String) {
        busyId = releaseId
        error = null
        scope.launch {
            runCatching { container.adminReleaseDecision(releaseId, action) }
                .onFailure { e ->
                    if (e is TotpRequiredException) {
                        pendingRetry = releaseId to action
                        totpPrompt = true
                    } else {
                        error = e.message
                    }
                }
                .onSuccess { reloadTick++ }
            busyId = null
        }
    }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("マイページ", style = MaterialTheme.typography.headlineSmall)
                    Text(state.email ?: state.userId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { container.authRepository.signOut() }) { Text("ログアウト") }
            }
            if (role != null) {
                Text(
                    "権限: ${if (role == "admin") "管理者" else "利用者"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
        }

        if (role == "admin") {
            item { SectionTitle("承認待ちのリリース") }
            if (pending.isEmpty()) {
                item { Text("承認待ちのリリースはありません。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)) }
            }
            items(pending, key = { it.id }) { r ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.appName} v${r.versionName ?: "?"}", style = MaterialTheme.typography.titleMedium)
                            r.developerName?.let { Text("開発者: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { decide(r.id, "publish") }, enabled = busyId != r.id) { Text("公開") }
                            OutlinedButton(onClick = { decide(r.id, "reject") }, enabled = busyId != r.id) { Text("却下") }
                        }
                    }
                    if (r.reasons.isEmpty()) {
                        Text("確認が必要な点の記録はありません(自動審査を通る前のリリースです)。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("確認が必要な点", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        r.reasons.forEach { Text("・$it", style = MaterialTheme.typography.bodySmall) }
                        Text("詳しい根拠は、Webのマイページで確認できます。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (role == "admin") {
            item { SectionTitle("管理者用アプリ") }
            if (privateApps.isEmpty()) {
                item { Text("管理者用のアプリはまだ公開されていません。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)) }
            }
            items(privateApps, key = { it.releaseId }) { app ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${app.name} v${app.versionName}", style = MaterialTheme.typography.titleMedium)
                    com.kawamonn.store.ui.InstallButton(
                        packageName = app.packageName,
                        latest = com.kawamonn.store.data.api.LatestReleaseDto(
                            releaseId = app.releaseId,
                            versionName = app.versionName,
                            versionCode = app.versionCode,
                            apkSize = app.apkSize,
                        ),
                    )
                }
            }
            item { Text("管理者だけがダウンロードできるアプリです(ストアの一覧・検索・更新の通知には出ません)。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
        }

        item { SectionTitle("作成したアプリ") }
        if (myApps.isEmpty()) {
            item { Text("まだアプリがありません。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)) }
        }
        items(myApps) { app ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium)
                    Text(appStatusLabel(app.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                app.releaseState?.let { Text("最新のリリース: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        item {
            val context = LocalContext.current
            HorizontalDivider(Modifier.padding(top = 12.dp))
            SectionTitle("自分のアプリを公開する")
            Text(
                "開発者として登録すると、自分で作ったアプリを、誰でもこのストアに公開できます。APKをアップロードすると自動で検査され、" +
                    "通信の機能や端末のデータを壊す可能性が見つからなければ、承認を待たずに公開されます(疑わしい点があるときは、運営が確認します)。" +
                    "登録とアップロードは、ブラウザの開発者ページで行います。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { openStoreWebPage(context, "/developer") }) { Text("開発者ページを開く") }
                TextButton(onClick = { openStoreWebPage(context, "/legal/developer") }) { Text("開発者向け規約") }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { openStoreWebPage(context, "/legal/terms") }) { Text("利用規約") }
                TextButton(onClick = { openStoreWebPage(context, "/legal/privacy") }) { Text("プライバシーポリシー") }
            }
        }

        item {
            Text(
                "インストール状況は「アップデート」タブで確認できます。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (totpPrompt) {
        fun close() {
            totpPrompt = false
            pendingRetry = null
            totpCode = ""
            totpError = null
        }
        AlertDialog(
            onDismissRequest = ::close,
            title = { Text("2段階認証") },
            text = {
                Column {
                    Text("この操作には2段階認証コードの入力が必要です。", modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = totpCode,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) totpCode = it },
                        label = { Text("6桁コード") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    totpError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !totpBusy && totpCode.length == 6,
                    onClick = {
                        totpBusy = true
                        totpError = null
                        scope.launch {
                            container.verifyTotp(totpCode)
                                .onSuccess {
                                    val retry = pendingRetry
                                    close()
                                    if (retry != null) decide(retry.first, retry.second)
                                }
                                .onFailure { totpError = it.message }
                            totpBusy = false
                        }
                    },
                ) { Text("確認") }
            },
            dismissButton = { OutlinedButton(onClick = ::close) { Text("キャンセル") } },
        )
    }
}
