package com.ttech.ideamemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ttech.ideamemo.domain.Category
import com.ttech.ideamemo.domain.Idea
import com.ttech.ideamemo.domain.SortOrder
import com.ttech.ideamemo.domain.Status
import com.ttech.ideamemo.domain.filterIdeas
import com.ttech.ideamemo.domain.sortIdeas

@Composable
fun ListScreen(
    ideas: List<Idea>,
    contentPadding: PaddingValues,
    onOpen: (Idea) -> Unit,
    onAdd: () -> Unit,
    onAddSample: () -> Unit,
    onImport: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<Status?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var order by rememberSaveable { mutableStateOf(SortOrder.PRIORITY) }
    var sortMenu by remember { mutableStateOf(false) }

    if (ideas.isEmpty()) {
        Column(Modifier.padding(contentPadding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyMessage(
                title = "思いついたアプリのアイデアを、ためていきましょう",
                body = "触れ込み・カテゴリ・作りたい度・メモを残しておくと、あとで見返したときに「これ作ろう」につながります。",
            ) {
                Button(onClick = onAdd) { Text("アイデアを追加") }
                OutlinedButton(onClick = onAddSample) { Text("サンプルを見てみる") }
                OutlinedButton(onClick = onImport) { Text("メモファイルを読み込む") }
            }
        }
        return
    }

    val shown = sortIdeas(filterIdeas(ideas, query, status, category), order)
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                placeholder = { Text("タイトル・メモで探す") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "消す") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = status == null, onClick = { status = null }, label = { Text("すべて ${ideas.size}") }) }
                items(Status.entries) { s ->
                    FilterChip(selected = status == s, onClick = { status = if (status == s) null else s }, label = { Text("${s.label} ${ideas.count { it.status == s }}") })
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries.filter { c -> ideas.any { it.category == c } }) { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c.label) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size}件", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    TextButton(onClick = { sortMenu = true }) { Text("並び順: ${order.label}") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { o -> DropdownMenuItem(text = { Text(o.label) }, onClick = { order = o; sortMenu = false }) }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item { EmptyMessage("見つかりません", "条件を変えるか、絞り込みを解除してください。") }
        }
        items(shown, key = { it.id }) { idea -> IdeaCard(idea, onClick = { onOpen(idea) }) }
    }
}

@Composable
private fun IdeaCard(idea: Idea, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(idea.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(idea.status.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            val summary = idea.summaryLine()
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stars(idea.priority)
            if (idea.memo.isNotEmpty()) Text(idea.memo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
