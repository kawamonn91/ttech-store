package com.ttech.travelwishlist.ui

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ttech.travelwishlist.domain.Category
import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.SortOrder
import com.ttech.travelwishlist.domain.Status
import com.ttech.travelwishlist.domain.filterPlaces
import com.ttech.travelwishlist.domain.monthsLabel
import com.ttech.travelwishlist.domain.sortPlaces
import com.ttech.travelwishlist.domain.yen

@Composable
fun ListScreen(
    places: List<Place>,
    currentMonth: Int,
    contentPadding: PaddingValues,
    onOpen: (Place) -> Unit,
    onAdd: () -> Unit,
    onAddSample: () -> Unit,
    onImport: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<Status?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var order by rememberSaveable { mutableStateOf(SortOrder.PRIORITY) }
    var sortMenu by remember { mutableStateOf(false) }

    if (places.isEmpty()) {
        Column(Modifier.padding(contentPadding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyMessage(
                title = "行きたい場所を、ためていきましょう",
                body = "温泉・絶景・グルメ。行きたい場所と時期、予算のメモを残しておくと、次の旅の計画がぐっと楽になります。",
            ) {
                Button(onClick = onAdd) { Text("行きたい場所を追加") }
                OutlinedButton(onClick = onAddSample) { Text("サンプルを見てみる") }
                OutlinedButton(onClick = onImport) { Text("メモファイルを読み込む") }
            }
        }
        return
    }

    val shown = sortPlaces(filterPlaces(places, query, status, category), order, currentMonth)
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
                placeholder = { Text("名前・場所・メモで探す") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "消す") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = status == null, onClick = { status = null }, label = { Text("すべて ${places.size}") }) }
                items(Status.entries) { s ->
                    FilterChip(selected = status == s, onClick = { status = if (status == s) null else s }, label = { Text("${s.label} ${places.count { it.status == s }}") })
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries.filter { c -> places.any { it.category == c } }) { c ->
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
        items(shown, key = { it.id }) { place -> PlaceCard(place, currentMonth, onClick = { onOpen(place) }) }
    }
}

@Composable
private fun PlaceCard(place: Place, currentMonth: Int, onClick: () -> Unit) {
    val visited = place.status == Status.VISITED
    SoftCard(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = null,
                    color = if (visited) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(place.status.label, style = MaterialTheme.typography.labelLarge, color = statusColor(place.status))
            }
            val summary = place.summaryLine()
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Stars(place.priority)
                if (place.months.isNotEmpty()) {
                    val now = currentMonth in place.months && !visited
                    Text(
                        monthsLabel(place.months) + if (now) "(今月)" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (now) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (place.budget > 0) Text(yen(place.budget), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (place.memo.isNotEmpty()) {
                Text(place.memo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}
