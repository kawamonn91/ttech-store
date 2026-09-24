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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import com.ttech.admin.data.DiaryEntry
import com.ttech.admin.domain.Labels

/** ひとこと日記の投稿を、非公開のものも含めて確認・削除する(本文で検索できる) */
@Composable
fun DiaryScreen(onOpenUser: (String) -> Unit) {
    val container = LocalContainer.current
    val actions = LocalActions.current
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val handle = rememberLoad(query) { container.api.diaryEntries(query) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("本文で検索") },
            singleLine = true,
            trailingIcon = { IconButton(onClick = { query = input.trim() }) { Icon(Icons.Filled.Search, contentDescription = "検索") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { query = input.trim() }),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        LoadContent(handle) { entries ->
            if (entries.isEmpty()) {
                EmptyView("投稿はありません")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { entry -> EntryCard(entry, container.http, onOpenUser, onDelete = { deleteId = entry.id }) }
                }
            }
        }
    }

    deleteId?.let { id ->
        ConfirmDialog(
            title = "投稿を削除",
            message = "この投稿(添付の写真も)を削除します。元には戻せません。",
            confirmLabel = "削除する",
            danger = true,
            onConfirm = { actions.run("削除しました", handle.reload) { container.api.deleteDiaryEntry(id) } },
            onDismiss = { deleteId = null },
        )
    }
}

@Composable
private fun EntryCard(entry: DiaryEntry, http: okhttp3.OkHttpClient, onOpenUser: (String) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(Labels.visibility(entry.visibility))
                Text(entry.userName.ifBlank { "(名前なし)" }, style = MaterialTheme.typography.labelLarge)
            }
            Text(entry.body)
            entry.photoUrl?.let { RemoteImage(it, http) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(Labels.dateTime(entry.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = { onOpenUser(entry.userId) }) { Text("投稿者") }
                    TextButton(onClick = onDelete) { Text("削除", color = DangerColor) }
                }
            }
        }
    }
}
