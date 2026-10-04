package com.ttech.attendancecount.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.attendancecount.domain.Attendee
import com.ttech.attendancecount.domain.EventItem
import com.ttech.attendancecount.domain.summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(event: EventItem, onBack: () -> Unit, onChange: (EventItem) -> Unit, onDelete: () -> Unit) {
    var showRename by remember { mutableStateOf(false) }
    var showAddAttendee by remember { mutableStateOf(false) }
    var deletingAttendee by remember { mutableStateOf<Attendee?>(null) }
    var confirmDeleteEvent by remember { mutableStateOf(false) }
    val summary = event.summary()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event.name, modifier = Modifier.clickable { showRename = true }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
                actions = { IconButton(onClick = { confirmDeleteEvent = true }) { Icon(Icons.Filled.Delete, contentDescription = "イベントを削除") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SummaryTile("出席", summary.checkedIn)
                        SummaryTile("欠席", summary.absent)
                        SummaryTile("飛び込み", summary.walkIns)
                        SummaryTile("合計", summary.total)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("飛び込み参加(事前登録なし)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(onClick = { onChange(event.copy(walkIns = (event.walkIns - 1).coerceAtLeast(0))) }, enabled = event.walkIns > 0) {
                                Icon(Icons.Filled.Remove, contentDescription = "飛び込みを1人減らす")
                            }
                            Text("${event.walkIns}人", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
                            FilledTonalIconButton(onClick = { onChange(event.copy(walkIns = event.walkIns + 1)) }) {
                                Icon(Icons.Filled.Add, contentDescription = "飛び込みを1人増やす")
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("参加者(${event.attendees.size}人)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { showAddAttendee = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("追加", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            items(event.attendees, key = { it.id }) { attendee ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = attendee.checkedIn,
                            onCheckedChange = { checked ->
                                onChange(event.copy(attendees = event.attendees.map { if (it.id == attendee.id) it.copy(checkedIn = checked) else it }))
                            },
                        )
                        Text(attendee.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { deletingAttendee = attendee }) {
                            Icon(Icons.Filled.Delete, contentDescription = "${attendee.name}を削除")
                        }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp)) }
        }
    }

    if (showRename) {
        NameInputDialog(
            title = "イベント名を変更",
            initial = event.name,
            label = "イベント名",
            onSave = { name -> onChange(event.copy(name = name)); showRename = false },
            onDismiss = { showRename = false },
        )
    }
    if (showAddAttendee) {
        NameInputDialog(
            title = "参加者を追加",
            initial = "",
            label = "参加者名",
            onSave = { name ->
                onChange(event.copy(attendees = event.attendees + Attendee(id = System.currentTimeMillis().toString(), name = name)))
                showAddAttendee = false
            },
            onDismiss = { showAddAttendee = false },
        )
    }
    deletingAttendee?.let { attendee ->
        ConfirmDialog(
            title = "参加者を削除しますか?",
            message = "「${attendee.name}」を参加者一覧から削除します。",
            confirmLabel = "削除する",
            onConfirm = {
                onChange(event.copy(attendees = event.attendees.filterNot { it.id == attendee.id }))
                deletingAttendee = null
            },
            onDismiss = { deletingAttendee = null },
        )
    }
    if (confirmDeleteEvent) {
        ConfirmDialog(
            title = "イベントを削除しますか?",
            message = "「${event.name}」と、登録した参加者・出席記録をすべて削除します。",
            confirmLabel = "削除する",
            onConfirm = { confirmDeleteEvent = false; onDelete() },
            onDismiss = { confirmDeleteEvent = false },
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
