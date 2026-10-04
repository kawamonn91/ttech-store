package com.ttech.attendancecount.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.ttech.attendancecount.domain.EventItem
import com.ttech.attendancecount.domain.summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(events: List<EventItem>, onOpen: (EventItem) -> Unit, onCreate: (String) -> Unit, onDelete: (EventItem) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EventItem?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("出席カウント") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = "イベントを追加") }
        },
    ) { padding ->
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("右下の + からイベントを作成してください", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp)) }
                items(events, key = { it.id }) { event ->
                    val s = event.summary()
                    Card(Modifier.fillMaxWidth().clickable { onOpen(event) }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(event.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "出席 ${s.checkedIn} / 登録 ${s.registered}" + if (s.walkIns > 0) "・飛び込み ${s.walkIns}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleting = event }) {
                                Icon(Icons.Filled.Delete, contentDescription = "${event.name}を削除")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        NameInputDialog(
            title = "イベントを作成",
            initial = "",
            label = "イベント名(例: 忘年会)",
            onSave = { name -> onCreate(name); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    deleting?.let { event ->
        ConfirmDialog(
            title = "イベントを削除しますか?",
            message = "「${event.name}」と、登録した参加者・出席記録をすべて削除します。",
            confirmLabel = "削除する",
            onConfirm = { onDelete(event); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}
