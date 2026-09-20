package com.kawamonn.store.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kawamonn.store.data.api.IndexDto
import com.kawamonn.store.data.api.StoreApi
import com.kawamonn.store.update.UpdateDetector

class UpdatesViewModel(private val api: StoreApi) : LoadViewModel<IndexDto>() {
    init {
        load()
    }

    override suspend fun fetch(): IndexDto = api.index()
}

@Composable
fun UpdatesScreen(onOpenApp: (slug: String) -> Unit, contentPadding: PaddingValues) {
    val container = LocalContainer.current
    val vm: UpdatesViewModel = viewModel(factory = factory(container) { UpdatesViewModel(it.api) })
    val state by vm.state.collectAsState()
    val tick by container.installController.installedTick.collectAsState()

    // 画面に戻るたびにインストール状況を取り直す(設定アプリで更新・削除された場合に備える)
    var resumeCount by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeCount++
        onPauseOrDispose {}
    }

    LoadContent(state, onRetry = vm::load) { index ->
        val installedVersions = remember(index, tick, resumeCount) {
            index.items.associate { it.packageName to container.installedApps.versionCode(it.packageName) }
        }
        val updates = UpdateDetector.detect(index.items) { installedVersions[it] }
        val updatePackages = updates.map { it.entry.packageName }.toSet()
        val upToDate = index.items.filter { installedVersions[it.packageName] != null && it.packageName !in updatePackages }

        LazyColumn(contentPadding = contentPadding) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("アップデート (${updates.size})", style = MaterialTheme.typography.titleLarge)
                    if (updates.size > 1) {
                        Button(onClick = {
                            container.installController.installSequentially(
                                updates.map { it.entry.packageName to it.entry.latest.releaseId },
                            )
                        }) { Text("すべて更新") }
                    }
                }
            }
            if (updates.isEmpty()) {
                item {
                    Text(
                        "更新できるアプリはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(updates, key = { "u-" + it.entry.packageName }) { update ->
                val entry = update.entry
                Column(
                    Modifier.fillMaxWidth().clickable { onOpenApp(entry.slug) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(entry.iconUrl, 48.dp)
                        Column {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "新バージョン ${entry.latest.versionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    InstallButton(entry.packageName, entry.latest)
                }
            }
            if (upToDate.isNotEmpty()) {
                item { SectionTitle("インストール済み") }
                items(upToDate, key = { "i-" + it.packageName }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenApp(entry.slug) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(entry.iconUrl, 48.dp)
                        Column {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "最新 (${entry.latest.versionName})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
