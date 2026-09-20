package com.kawamonn.store.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
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
fun HomeScreen(onOpenApp: (slug: String) -> Unit, contentPadding: PaddingValues) {
    val container = LocalContainer.current
    val vm: HomeViewModel = viewModel(factory = factory(container) { HomeViewModel(it.api) })
    val state by vm.state.collectAsState()

    LoadContent(state, onRetry = vm::load) { home ->
        LazyColumn(contentPadding = contentPadding) {
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
