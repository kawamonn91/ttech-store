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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull

private data class MyApp(val name: String, val status: String)
private data class PendingRelease(val id: String, val appName: String, val versionName: String?, val status: String)

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

            val appRows = postgrest.select("apps", "developer_id=eq.${state.userId}&select=name,status&order=created_at.desc")
            myApps = appRows.map { MyApp(it.jsonObject["name"]!!.jsonPrimitive.content, it.jsonObject["status"]!!.jsonPrimitive.content) }

            if (role == "admin") {
                val releaseRows = postgrest.select(
                    "app_releases",
                    "status=in.(scanned,approved)&select=id,version_name,status,apps(name)&order=created_at.desc",
                )
                pending = releaseRows.map {
                    val obj = it.jsonObject
                    val app = (obj["apps"] as? JsonObject)
                    PendingRelease(
                        id = obj["id"]!!.jsonPrimitive.content,
                        appName = app?.get("name")?.jsonPrimitive?.contentOrNull ?: "?",
                        versionName = obj["version_name"]?.jsonPrimitive?.contentOrNull,
                        status = obj["status"]!!.jsonPrimitive.content,
                    )
                }
            } else {
                pending = emptyList()
            }
        }.onFailure { error = it.message }
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
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${r.appName} v${r.versionName ?: "?"}", modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { decide(r.id, "publish") }, enabled = busyId != r.id) { Text("公開") }
                        OutlinedButton(onClick = { decide(r.id, "reject") }, enabled = busyId != r.id) { Text("却下") }
                    }
                }
            }
        }

        item { SectionTitle("作成したアプリ") }
        if (myApps.isEmpty()) {
            item { Text("まだアプリがありません。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)) }
        }
        items(myApps) { app ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.name)
                Text(app.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
