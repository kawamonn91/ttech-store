package com.ttech.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ttech.admin.data.Overview

/** ホーム: 全サービスの状況のまとめ。気にするべきもの(未対応の報告など)を先頭に置く */
@Composable
fun HomeScreen(onOpenReports: () -> Unit, onOpenUsers: () -> Unit, onOpenMore: () -> Unit) {
    val container = LocalContainer.current
    val handle = rememberLoad { container.api.overview() }

    LoadContent(handle) { o ->
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("T-tech 管理", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = handle.reload, enabled = !handle.loading) { Text(if (handle.loading) "更新中…" else "更新") }
            }

            AttentionCard(o, onOpenReports, onOpenMore)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("ユーザー", o.users.total.toString(), "直近7日 +${o.users.new7d}", Modifier.weight(1f), onOpenUsers)
                StatCard("BAN中", o.users.banned.toString(), "全サービス共通", Modifier.weight(1f), onOpenUsers)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("ひとこと日記", o.diary.entries.toString(), "投稿の総数", Modifier.weight(1f))
                StatCard("公開アプリ", o.apps.published.toString(), "ストアに公開中", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("ダウンロード", o.downloads.total.toString(), "直近7日 +${o.downloads.last7d}", Modifier.weight(1f))
                StatCard("承認待ちリリース", o.releases.pendingApproval.toString(), "アプリの公開待ち", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttentionCard(o: Overview, onOpenReports: () -> Unit, onOpenMore: () -> Unit) {
    val items = buildList {
        if (o.openReports > 0) add(Triple("未対応の報告が ${o.openReports} 件あります", DangerColor, onOpenReports))
        if (o.developers.pending > 0) add(Triple("開発者の申請が ${o.developers.pending} 件、承認待ちです", WarnColor, onOpenMore))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("対応が必要なもの", style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text("いま対応が必要なものはありません", color = OkColor)
            } else {
                items.forEach { (text, color, onClick) ->
                    Text(text, color = color, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, note: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(if (onClick != null) modifier.clickable(onClick = onClick) else modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
