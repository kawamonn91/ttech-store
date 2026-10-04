package com.ttech.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.ttech.admin.BuildConfig
import com.ttech.admin.data.AppRow
import com.ttech.admin.data.AuditRow
import com.ttech.admin.data.Developer
import com.ttech.admin.data.PendingRelease
import com.ttech.admin.domain.Labels
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private enum class Section(val title: String) { Releases("リリースの承認"), Developers("開発者"), Apps("アプリ"), Audit("操作の記録(監査ログ)") }

/** その他: 開発者・アプリ・監査ログ・アカウント */
@Composable
fun MoreScreen() {
    var section by rememberSaveable { mutableStateOf<Section?>(null) }
    BackHandler(enabled = section != null) { section = null }

    when (section) {
        null -> MoreMenu(onOpen = { section = it })
        else -> Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                IconButton(onClick = { section = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                Text(section!!.title, style = MaterialTheme.typography.titleLarge)
            }
            when (section) {
                Section.Releases -> ReleasesList()
                Section.Developers -> DevelopersList()
                Section.Apps -> AppsList()
                Section.Audit -> AuditList()
                null -> Unit
            }
        }
    }
}

@Composable
private fun MoreMenu(onOpen: (Section) -> Unit) {
    val container = LocalContainer.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val session by container.auth.session.collectAsState()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("その他", style = MaterialTheme.typography.headlineSmall)
        Section.entries.forEach { s ->
            ClickableCard(onClick = { onOpen(s) }) { Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
        }
        SectionTitle("アカウント")
        Text("ログイン中: ${session?.email ?: "-"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) { Text("ログアウト") }
        Text("T-tech 管理 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }

    if (confirmLogout) ConfirmDialog(
        title = "ログアウト",
        message = "ログアウトします。次に使うときは、もう一度ログインが必要です。",
        confirmLabel = "ログアウト",
        onConfirm = { scope.launch { container.auth.signOut() } },
        onDismiss = { confirmLogout = false },
    )
}

@Composable
private fun DevelopersList() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val handle = rememberLoad { container.api.developers() }
    var confirm by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) } // userId to status

    LoadContent(handle) { list ->
        if (list.isEmpty()) EmptyView("開発者はいません") else LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.userId }) { d -> DeveloperCard(d, onDecide = { status -> confirm = d.userId to status }) }
        }
    }

    confirm?.let { (userId, status) ->
        ConfirmDialog(
            title = if (status == "approved") "開発者を承認" else "開発者を停止",
            message = if (status == "approved") "この開発者がアプリを登録できるようになります。" else "この開発者はアプリを登録・更新できなくなります。",
            confirmLabel = if (status == "approved") "承認する" else "停止する",
            danger = status != "approved",
            onConfirm = { actions.run(if (status == "approved") "承認しました" else "停止しました", handle.reload) { container.api.setDeveloperStatus(userId, status) } },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun DeveloperCard(d: Developer, onDecide: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                Badge(Labels.developerStatus(d.status), if (d.status == "approved") OkColor else if (d.status == "pending") WarnColor else DangerColor)
            }
            Text(d.contactEmail, style = MaterialTheme.typography.bodyMedium)
            d.website?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("申請日: ${Labels.date(d.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.status != "approved") Button(onClick = { onDecide("approved") }) { Text("承認する") }
                if (d.status != "suspended") TextButton(onClick = { onDecide("suspended") }) { Text("停止する", color = DangerColor) }
            }
        }
    }
}

@Composable
private fun ReleasesList() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val handle = rememberLoad { container.api.pendingReleases() }
    var confirm by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) } // releaseId to action

    LoadContent(handle) { list ->
        if (list.isEmpty()) EmptyView("承認待ちのリリースはありません") else LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { r -> ReleaseCard(r, onDecide = { action -> confirm = r.id to action }) }
        }
    }

    confirm?.let { (releaseId, action) ->
        ConfirmDialog(
            title = when (action) {
                "publish" -> "リリースを公開"
                "reject" -> "リリースを却下"
                else -> "公開を停止"
            },
            message = when (action) {
                "publish" -> "ストアに表示され、ダウンロードできるようになります。"
                "reject" -> "このリリースは却下され、公開されません。"
                else -> "ストアから非表示になります。"
            },
            confirmLabel = when (action) {
                "publish" -> "公開する"
                "reject" -> "却下する"
                else -> "停止する"
            },
            danger = action != "publish",
            onConfirm = {
                actions.run(if (action == "publish") "公開しました" else if (action == "reject") "却下しました" else "公開を停止しました", handle.reload) {
                    container.api.decideRelease(releaseId, action)
                }
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun ReleaseCard(r: PendingRelease, onDecide: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.app?.name ?: "-", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                Text("v${r.versionName ?: "?"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Badge(Labels.releaseStatus(r.status), if (r.status == "scanned") WarnColor else MaterialTheme.colorScheme.primary)
            }
            if (r.policyVerdict == "needs_review") {
                Text("自動審査で確認が必要な点が見つかりました。内容を見て、公開・却下を決めてください", style = MaterialTheme.typography.bodySmall, color = WarnColor, fontWeight = FontWeight.Bold)
                r.policyFindings.forEach { f ->
                    val title = (f["title"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    Text("・$title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide("publish") }) { Text("公開する") }
                TextButton(onClick = { onDecide("reject") }) { Text("却下", color = DangerColor) }
            }
        }
    }
}

@Composable
private fun AppsList() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val handle = rememberLoad { container.api.apps() }
    var confirm by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) } // appId to status

    LoadContent(handle) { list ->
        if (list.isEmpty()) EmptyView("アプリはありません") else LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { a -> AppCard(a, onSet = { status -> confirm = a.id to status }) }
        }
    }

    confirm?.let { (appId, status) ->
        ConfirmDialog(
            title = if (status == "published") "アプリを公開" else "アプリを公開停止",
            message = if (status == "published") "ストアに表示され、ダウンロードできるようになります。" else "ストアから非表示になり、新しくダウンロードできなくなります(インストール済みの端末には影響しません)。",
            confirmLabel = if (status == "published") "公開する" else "停止する",
            danger = status != "published",
            onConfirm = { actions.run(if (status == "published") "公開しました" else "公開を停止しました", handle.reload) { container.api.setAppStatus(appId, status) } },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun AppCard(a: AppRow, onSet: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                Badge(Labels.appStatus(a.status), if (a.status == "published") OkColor else WarnColor)
                if (a.adminOnly) Badge("管理者専用", DangerColor)
            }
            Text(a.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "v${a.latestVersion ?: "-"} ・ ${a.downloadCount} DL ・ ★${"%.1f".format(a.ratingAvg)}(${a.ratingCount})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (a.status == "published") {
                TextButton(onClick = { onSet("suspended") }) { Text("公開を停止する", color = DangerColor) }
            } else {
                TextButton(onClick = { onSet("published") }) { Text("公開する") }
            }
        }
    }
}

@Composable
private fun AuditList() {
    val container = LocalContainer.current
    val handle = rememberLoad { container.api.audit() }
    LoadContent(handle) { list ->
        if (list.isEmpty()) EmptyView("記録はありません") else LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { row -> AuditCard(row) }
        }
    }
}

@Composable
private fun AuditCard(row: AuditRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Labels.auditAction(row.action), style = MaterialTheme.typography.titleMedium)
            Text("${row.adminName} ・ ${Labels.dateTime(row.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.detail["reason"]?.let { Text("理由: ${it.toString().trim('"')}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
