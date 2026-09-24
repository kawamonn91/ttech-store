package com.ttech.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ttech.admin.data.Report
import com.ttech.admin.domain.Labels
import com.ttech.admin.domain.ReportAction
import com.ttech.admin.domain.ReportRules

/** 報告(ひとこと日記の報告 + アプリのレビュー通報)の一覧と処理 */
@Composable
fun ReportsScreen(onOpenUser: (String) -> Unit) {
    val container = LocalContainer.current
    var includeResolved by rememberSaveable { mutableStateOf(false) }
    val handle = rememberLoad(includeResolved) { container.api.reports(includeResolved) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !includeResolved, onClick = { includeResolved = false }, label = { Text("未対応") })
            FilterChip(selected = includeResolved, onClick = { includeResolved = true }, label = { Text("すべて") })
        }
        LoadContent(handle) { reports ->
            if (reports.isEmpty()) {
                EmptyView(if (includeResolved) "報告はありません" else "未対応の報告はありません")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reports, key = { "${it.kind}:${it.id}" }) { report ->
                        ReportCard(report, onClick = { selected = "${report.kind}:${report.id}" })
                    }
                }
            }
        }
    }

    val current = (handle.state as? LoadState.Ready)?.value?.firstOrNull { "${it.kind}:${it.id}" == selected }
    if (current != null) {
        ReportDialog(current, onDismiss = { selected = null }, onOpenUser = { selected = null; onOpenUser(it) }, onDone = { selected = null; handle.reload() })
    }
}

@Composable
private fun ReportCard(report: Report, onClick: () -> Unit) {
    ClickableCard(onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(if (report.kind == "review") "レビュー" else "日記")
                Badge(Labels.reason(report.reason), WarnColor)
                Badge(Labels.reportStatus(report.status), if (ReportRules.isOpen(report.status)) DangerColor else OkColor)
            }
            Text(report.body.ifBlank { "(本文なし)" }, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                "報告者: ${report.reporter.name} → 対象: ${report.target.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(Labels.dateTime(report.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReportDialog(report: Report, onDismiss: () -> Unit, onOpenUser: (String) -> Unit, onDone: () -> Unit) {
    val container = LocalContainer.current
    val actions = LocalActions.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) } // 確認待ちの操作の id
    val available = ReportRules.availableActions(report.status, report.contentId, report.target.userId)

    fun perform(action: ReportAction, reason: String? = null) {
        actions.run(successMessage = "処理しました", onDone = onDone) {
            container.api.resolveReport(report.kind, report.id, action.id, reason)
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(report.contentLabel.ifBlank { "報告" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(Labels.reason(report.reason), WarnColor)
                    Badge(Labels.reportStatus(report.status), if (ReportRules.isOpen(report.status)) DangerColor else OkColor)
                }
                if (!report.detail.isNullOrBlank()) Text("報告者のコメント: ${report.detail}", style = MaterialTheme.typography.bodyMedium)
                Text(report.body.ifBlank { "(本文なし)" })
                Text("報告者: ${report.reporter.name}", style = MaterialTheme.typography.bodySmall)
                Text("対象: ${report.target.name}", style = MaterialTheme.typography.bodySmall)
                report.target.userId?.let { uid -> TextButton(onClick = { onOpenUser(uid) }) { Text("対象のユーザーを見る") } }
                if (report.contentId == null && ReportRules.isOpen(report.status)) {
                    Text("報告された内容は、すでに削除されています。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    available.forEach { action ->
                        val label = if (action == ReportAction.DeleteContent) ReportRules.deleteLabel(report.kind) else action.label
                        if (action.destructive) {
                            Button(
                                onClick = { pending = action.id },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { perform(action) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )

    when (pending) {
        ReportAction.DeleteContent.id -> ConfirmDialog(
            title = ReportRules.deleteLabel(report.kind),
            message = if (report.kind == "review") "このレビューを非表示にし、報告を対応済みにします。" else "この投稿(添付の写真も)を削除し、報告を対応済みにします。元には戻せません。",
            confirmLabel = "実行する",
            danger = true,
            onConfirm = { perform(ReportAction.DeleteContent) },
            onDismiss = { pending = null },
        )
        ReportAction.BanAuthor.id -> ReasonDialog(
            title = "投稿者をBAN",
            message = "${report.target.name} さんをBANします。ひとこと日記・ストアのレビューなど、このアカウントでの操作がすべてできなくなります(解除はユーザー画面から)。",
            confirmLabel = "BANする",
            onConfirm = { reason -> perform(ReportAction.BanAuthor, reason) },
            onDismiss = { pending = null },
        )
    }
}
