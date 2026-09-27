package com.kawamonn.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kawamonn.store.data.api.ApiException
import com.kawamonn.store.data.api.AppSummaryDto
import com.kawamonn.store.data.api.CategoryDto
import com.kawamonn.store.data.api.StoreApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 1回のリクエストで取る件数(APIの上限) */
private const val PAGE_SIZE = 100

private data class SortOption(val key: String, val label: String)

private val SORT_OPTIONS = listOf(
    SortOption("new", "新着順"),
    SortOption("popular", "人気順"),
    SortOption("rating", "評価順"),
)

class AllAppsViewModel(private val api: StoreApi) : ViewModel() {
    private val _sort = MutableStateFlow(SORT_OPTIONS.first().key)
    val sort: StateFlow<String> = _sort.asStateFlow()

    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryDto>>(emptyList())
    val categories: StateFlow<List<CategoryDto>> = _categories.asStateFlow()

    private val _results = MutableStateFlow<Load<List<AppSummaryDto>>>(Load.Loading)
    val results: StateFlow<Load<List<AppSummaryDto>>> = _results.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch { runCatching { _categories.value = api.categories().items } }
        load()
    }

    fun setSort(key: String) {
        _sort.value = key
        load()
    }

    fun setCategory(slug: String?) {
        _category.value = slug
        load()
    }

    /** 件数が増えても全部見えるように、1回で取り切れない分は続きのページも取る(APIは1回100件まで) */
    private suspend fun fetchAll(): List<AppSummaryDto> {
        val all = mutableListOf<AppSummaryDto>()
        while (true) {
            val page = api.apps(query = null, category = _category.value, sort = _sort.value, limit = PAGE_SIZE, offset = all.size)
            all += page.items
            if (page.items.isEmpty() || all.size >= page.total) return all
        }
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _results.value = Load.Loading
            _results.value = try {
                Load.Ready(fetchAll())
            } catch (e: ApiException) {
                Load.Failed(e.message ?: "読み込みに失敗しました")
            }
        }
    }
}

/** ストアの全アプリを、カテゴリ・並び順で絞り込んで一覧できる画面 */
@Composable
fun AllAppsScreen(onOpenApp: (slug: String) -> Unit, contentPadding: PaddingValues) {
    val container = LocalContainer.current
    val vm: AllAppsViewModel = viewModel(factory = factory(container) { AllAppsViewModel(it.api) })
    val sort by vm.sort.collectAsState()
    val category by vm.category.collectAsState()
    val categories by vm.categories.collectAsState()
    val results by vm.results.collectAsState()

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SORT_OPTIONS, key = { it.key }) { s ->
                FilterChip(selected = sort == s.key, onClick = { vm.setSort(s.key) }, label = { Text(s.label) })
            }
        }
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = category == null, onClick = { vm.setCategory(null) }, label = { Text("すべてのカテゴリ") })
                }
                items(categories, key = { it.slug }) { c ->
                    FilterChip(selected = category == c.slug, onClick = { vm.setCategory(c.slug) }, label = { Text(c.name) })
                }
            }
        }
        Box(Modifier.weight(1f).padding(top = 8.dp)) {
            LoadContent(results, onRetry = { vm.load() }) { apps ->
                if (apps.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("アプリが見つかりませんでした", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
                        items(apps, key = { it.id }) { AppRow(it, onClick = { onOpenApp(it.slug) }) }
                    }
                }
            }
        }
    }
}
