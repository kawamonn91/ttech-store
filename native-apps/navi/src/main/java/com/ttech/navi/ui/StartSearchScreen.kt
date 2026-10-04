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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ttech.navi.NaviContainer
import com.ttech.navi.data.CurrentLocation
import com.ttech.navi.domain.NaviException
import com.ttech.navi.domain.Place
import kotlinx.coroutines.launch

private sealed interface StartSearchState {
    data object Idle : StartSearchState
    data object Loading : StartSearchState
    data class Found(val places: List<Place>) : StartSearchState
    data class Failed(val message: String) : StartSearchState
}

/** 出発地を選ぶ画面。検索して選ぶか、地図で選ぶか、「現在地を使う」で既定(現在地)に戻せる */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSearchScreen(container: NaviContainer, onPicked: (Place?) -> Unit, onPickOnMap: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val perm by rememberPermState()
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<StartSearchState>(StartSearchState.Idle) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        focusManager.clearFocus()
        state = StartSearchState.Loading
        scope.launch {
            state = try {
                val near = if (perm.location) CurrentLocation.lastKnown(context) else null
                val places = container.search.search(q, near)
                if (places.isEmpty()) StartSearchState.Failed("見つかりませんでした。駅名や施設名、住所で試してみてください") else StartSearchState.Found(places)
            } catch (e: NaviException) {
                StartSearchState.Failed(e.message ?: "検索できませんでした")
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("出発地を選ぶ") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
        )
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().clickable { onPicked(null) }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("現在地を使う", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("出発地(駅名・施設名・住所)") },
                    singleLine = true,
                    leadingIcon = {
                        IconButton(onClick = ::search, enabled = query.isNotBlank()) { Icon(Icons.Filled.Search, contentDescription = "検索") }
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; state = StartSearchState.Idle }) { Icon(Icons.Filled.Close, contentDescription = "消す") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
            }
            item {
                Text(
                    "地図で選ぶ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onPickOnMap).padding(vertical = 4.dp),
                )
            }

            when (val s = state) {
                StartSearchState.Idle -> {}
                StartSearchState.Loading -> item {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("探しています…")
                    }
                }
                is StartSearchState.Failed -> item { Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                is StartSearchState.Found -> {
                    item { SectionTitle("検索結果") }
                    items(s.places) { p -> PlaceRow(p, onClick = { onPicked(p) }) }
                }
            }
        }
    }
}
