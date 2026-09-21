package com.kawamonn.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kawamonn.store.data.api.HomeDto
import com.kawamonn.store.data.api.StoreApi

class HomeViewModel(private val api: StoreApi) : LoadViewModel<HomeDto>() {
    init {
        load()
    }

    override suspend fun fetch(): HomeDto = api.home()
}

@Composable
fun HomeScreen(onOpenApp: (slug: String) -> Unit, onOpenUpdates: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalContainer.current
    val vm: HomeViewModel = viewModel(factory = factory(container) { HomeViewModel(it.api) })
    val state by vm.state.collectAsState()
    val updateCount by container.updateChecker.updates.collectAsState()
    val bannerDismissed by container.updateChecker.bannerDismissed.collectAsState()

    LoadContent(state, onRetry = vm::load) { home ->
        LazyColumn(contentPadding = contentPadding) {
            if (updateCount.isNotEmpty() && !bannerDismissed) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { onOpenUpdates() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "${updateCount.size}件のアップデートがあります",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { container.updateChecker.dismissBanner() }) {
                            Icon(Icons.Filled.Close, contentDescription = "閉じる", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (home.featured.isNotEmpty()) {
                item { SectionTitle("おすすめ") }
                items(home.featured, key = { "f-" + it.id }) { AppRow(it, onClick = { onOpenApp(it.slug) }) }
            }
            if (home.newest.isNotEmpty()) {
                item { SectionTitle("新着") }
                items(home.newest, key = { "n-" + it.id }) { AppRow(it, onClick = { onOpenApp(it.slug) }) }
            }
            if (home.popular.isNotEmpty()) {
                item { SectionTitle("人気") }
                items(home.popular, key = { "p-" + it.id }) { AppRow(it, onClick = { onOpenApp(it.slug) }) }
            }
            if (home.featured.isEmpty() && home.newest.isEmpty() && home.popular.isEmpty()) {
                item { SectionTitle("公開中のアプリはまだありません") }
            }
        }
    }
}
