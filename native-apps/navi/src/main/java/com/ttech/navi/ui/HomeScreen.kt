package com.ttech.navi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.navi.NaviContainer
import com.ttech.navi.data.CurrentLocation
import com.ttech.navi.domain.NaviException
import com.ttech.navi.domain.Place
import kotlinx.coroutines.launch

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Found(val places: List<Place>) : SearchState
    data class Failed(val message: String) : SearchState
}

@Composable
fun HomeScreen(container: NaviContainer, onPick: (Place) -> Unit, onPickOnMap: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val perm by rememberPermState()
    val actions = rememberPermissionActions()
    val recents by container.recents.places.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        state = SearchState.Loading
        scope.launch {
            state = try {
                val near = if (perm.location) CurrentLocation.lastKnown(context) else null
                val places = container.search.search(q, near)
                if (places.isEmpty()) SearchState.Failed("見つかりませんでした。駅名や施設名、住所で試してみてください") else SearchState.Found(places)
            } catch (e: NaviException) {
                SearchState.Failed(e.message ?: "検索できませんでした")
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("先読みナビ", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "設定") }
            }
            Text("どこへ行きますか?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (!perm.canNavigate) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!perm.location) {
                            Text("ナビには、位置情報の許可が必要です", style = MaterialTheme.typography.titleSmall)
                            Button(onClick = actions.requestLocation) { Text("位置情報を許可する") }
                        } else {
                            Text("端末の位置情報(GPS)がオフです", style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick = actions.openLocationSettings) { Text("設定を開く") }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("目的地(駅名・施設名・住所)") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; state = SearchState.Idle }) { Icon(Icons.Filled.Close, contentDescription = "消す") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::search, enabled = query.isNotBlank() && state != SearchState.Loading) { Text("検索") }
                OutlinedButton(onClick = onPickOnMap) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  地図で選ぶ")
                }
            }
        }

        when (val s = state) {
            SearchState.Idle -> {}
            SearchState.Loading -> item {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("探しています…")
                }
            }
            is SearchState.Failed -> item { Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            is SearchState.Found -> {
                item { SectionTitle("検索結果") }
                items(s.places) { p -> PlaceRow(p, onClick = { onPick(p) }) }
            }
        }

        if (recents.isNotEmpty() && state !is SearchState.Found) {
            item { SectionTitle("最近の目的地") }
            items(recents) { p ->
                PlaceRow(p, onClick = { onPick(p) }, onRemove = { scope.launch { container.recents.remove(p) } })
            }
        }

        item { Features() }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (place.detail.isNotEmpty()) Text(place.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "履歴から消す") }
        }
    }
}

@Composable
private fun Features() {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("先読みナビでできること", style = MaterialTheme.typography.titleSmall)
            listOf(
                "曲がる先の、もう一つ先の曲がる方向も、あらかじめ声でお知らせ",
                "都道府県・市区町村をまたいだときに、声でお知らせ",
                "出発時に、到着予定時刻と、目的地・道中の天気をお知らせ",
                "到着したときに、走った距離とかかった時間をお知らせ",
            ).forEach { Text("・$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
