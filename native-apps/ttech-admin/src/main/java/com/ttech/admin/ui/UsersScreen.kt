package com.ttech.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttech.admin.data.UserDetail
import com.ttech.admin.data.UserRow
import com.ttech.admin.domain.Labels

/** ユーザー一覧(全サービス共通のアカウント)。メール・表示名で検索し、BAN中だけに絞れる */
@Composable
fun UsersScreen(onOpenUser: (String) -> Unit) {
    val container = LocalContainer.current
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var bannedOnly by rememberSaveable { mutableStateOf(false) }
    val handle = rememberLoad(query, bannedOnly) { container.api.users(query, bannedOnly) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("メールアドレス・表示名で検索") },
            singleLine = true,
            trailingIcon = { IconButton(onClick = { query = input.trim() }) { Icon(Icons.Filled.Search, contentDescription = "検索") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { query = input.trim() }),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = bannedOnly, onClick = { bannedOnly = !bannedOnly }, label = { Text("BAN中のみ") })
        }
        LoadContent(handle) { page ->
            if (page.items.isEmpty()) {
                EmptyView("該当するユーザーはいません")
            } else {
                Text("${page.total} 人", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    items(page.items, key = { it.id }) { user -> UserCard(user) { onOpenUser(user.id) } }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: UserRow, onClick: () -> Unit) {
    ClickableCard(onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(user.displayName.ifBlank { "(名前なし)" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (user.isBanned) Badge("BAN中", DangerColor)
                if (user.role != "user") Badge(Labels.role(user.role))
            }
            Text(user.email ?: "(メールなし)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Labels.provider(user.provider)} ・ 日記 ${user.diaryCount} ・ レビュー ${user.reviewCount} ・ 報告された ${user.reportsAgainst}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** ユーザー1人の詳細。アカウント情報・各サービスでの活動・報告された履歴を見て、BAN/解除ができる */
@Composable
fun UserDetailScreen(userId: String, onBack: () -> Unit, onOpenUser: (String) -> Unit) {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val handle = rememberLoad(userId) { container.api.userDetail(userId) }
    var showBan by rememberSaveable { mutableStateOf(false) }
    var showUnban by rememberSaveable { mutableStateOf(false) }
    var deleteEntryId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
            Text("ユーザーの詳細", style = MaterialTheme.typography.titleLarge)
        }
        LoadContent(handle) { d ->
            UserDetailBody(
                d,
                onBan = { showBan = true },
                onUnban = { showUnban = true },
                onDeleteEntry = { deleteEntryId = it },
                onToggleReview = { id, hide ->
                    actions.run(if (hide) "レビューを非表示にしました" else "レビューを再表示しました", handle.reload) {
                        container.api.setReviewStatus(id, if (hide) "hidden" else "visible")
                    }
                },
            )
            if (showBan) ReasonDialog(
                title = "BANする",
                message = "${d.displayName.ifBlank { d.email ?: "このユーザー" }} をBANします。ひとこと日記・ストアのレビューなど、このアカウントでの操作がすべてできなくなり、ログインもできなくなります。",
                confirmLabel = "BANする",
                onConfirm = { reason -> actions.run("BANしました", handle.reload) { container.api.ban(userId, reason) } },
                onDismiss = { showBan = false },
            )
            if (showUnban) ConfirmDialog(
                title = "BANを解除",
                message = "BANを解除し、再びログイン・利用できるようにします。",
                confirmLabel = "解除する",
                onConfirm = { actions.run("BANを解除しました", handle.reload) { container.api.unban(userId) } },
                onDismiss = { showUnban = false },
            )
            deleteEntryId?.let { entryId ->
                ConfirmDialog(
                    title = "投稿を削除",
                    message = "この投稿(添付の写真も)を削除します。元には戻せません。",
                    confirmLabel = "削除する",
                    danger = true,
                    onConfirm = { actions.run("削除しました", handle.reload) { container.api.deleteDiaryEntry(entryId) } },
                    onDismiss = { deleteEntryId = null },
                )
            }
        }
    }
}

@Composable
private fun UserDetailBody(
    d: UserDetail,
    onBan: () -> Unit,
    onUnban: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onToggleReview: (id: String, hide: Boolean) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.displayName.ifBlank { "(名前なし)" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
                if (d.isBanned) Badge("BAN中", DangerColor)
            }
            Text(d.email ?: "(メールなし)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionTitle("アカウント")
            InfoRow("権限", Labels.role(d.role))
            InfoRow("ログイン方法", Labels.provider(d.provider))
            InfoRow("メール確認", if (d.emailConfirmed) "確認済み" else "未確認")
            InfoRow("登録日", Labels.dateTime(d.createdAt))
            InfoRow("最終ログイン", Labels.dateTime(d.lastSignInAt))
            InfoRow("ユーザーID", d.id)
            d.developer?.let {
                InfoRow("開発者", "${it.name}(${Labels.developerStatus(it.status)})")
                InfoRow("連絡先", it.contactEmail)
            }
        }
        if (d.isBanned) {
            item {
                SectionTitle("BANの情報")
                InfoRow("BANした日時", Labels.dateTime(d.bannedAt))
                InfoRow("理由", d.banReason?.ifBlank { null } ?: "(記録なし)")
            }
        }
        item {
            SectionTitle("各サービスでの活動")
            InfoRow("ひとこと日記", "${d.counts.diaryEntries} 件")
            InfoRow("アプリのレビュー", "${d.counts.reviews} 件")
            InfoRow("ダウンロード", "${d.counts.downloads} 回")
            InfoRow("報告された", "${d.counts.reportsAgainst} 件")
            InfoRow("報告した", "${d.counts.reportsFiled} 件")
        }
        item {
            if (d.role == "admin") {
                Text("管理者はBANできません。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            } else if (d.isBanned) {
                androidx.compose.material3.OutlinedButton(onClick = onUnban, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("BANを解除する") }
            } else {
                androidx.compose.material3.Button(
                    onClick = onBan,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DangerColor),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) { Text("このユーザーをBANする") }
            }
        }
        if (d.reportsAgainst.isNotEmpty()) {
            item { SectionTitle("報告された履歴") }
            items(d.reportsAgainst, key = { "r-${it.id}" }) { r ->
                androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge(Labels.reason(r.reason), WarnColor)
                            Badge(Labels.reportStatus(r.status), if (r.status == "open") DangerColor else OkColor)
                        }
                        Text(r.entryBody ?: "(本文なし)", maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(Labels.dateTime(r.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (d.recentEntries.isNotEmpty()) {
            item { SectionTitle("最近の日記の投稿") }
            items(d.recentEntries, key = { "e-${it.id}" }) { e ->
                androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge(Labels.visibility(e.visibility))
                            if (e.hasPhoto) Badge("写真あり", WarnColor)
                        }
                        Text(e.body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(Labels.dateTime(e.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onDeleteEntry(e.id) }) { Text("削除", color = DangerColor) }
                        }
                    }
                }
            }
        }
        if (d.recentReviews.isNotEmpty()) {
            item { SectionTitle("最近のレビュー") }
            items(d.recentReviews, key = { "v-${it.id}" }) { r ->
                androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge("★${r.rating}")
                            Badge(Labels.reviewStatus(r.status), if (r.status == "hidden") DangerColor else OkColor)
                            Text(r.appName, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(r.body.ifBlank { "(本文なし)" }, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(Labels.dateTime(r.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onToggleReview(r.id, r.status != "hidden") }) { Text(if (r.status == "hidden") "再表示" else "非表示にする") }
                        }
                    }
                }
            }
        }
    }
}
