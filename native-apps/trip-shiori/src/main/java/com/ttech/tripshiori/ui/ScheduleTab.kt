package com.ttech.tripshiori.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.tripshiori.domain.ScheduleItem
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.TripPhase
import com.ttech.tripshiori.domain.dayCount
import com.ttech.tripshiori.domain.dayLabel
import com.ttech.tripshiori.domain.itemsOn
import com.ttech.tripshiori.domain.phase
import com.ttech.tripshiori.domain.removeItem
import com.ttech.tripshiori.domain.upsertItem
import com.ttech.tripshiori.domain.yen
import java.time.LocalDate

/** 地図アプリで場所を開く(端末の地図アプリに渡すだけで、このアプリは通信しない) */
fun openPlaceInMaps(context: android.content.Context, place: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "地図アプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ScheduleTab(trip: Trip, today: LocalDate, onUpdate: ((Trip) -> Trip) -> Unit) {
    val context = LocalContext.current
    val phase = trip.phase(today)
    var day by rememberSaveable(trip.id) { mutableStateOf((phase as? TripPhase.Ongoing)?.day ?: 0) }
    if (day >= trip.dayCount()) day = trip.dayCount() - 1
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val items = trip.itemsOn(day)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(trip.dayCount()) { d ->
                    FilterChip(
                        selected = d == day,
                        onClick = { day = d },
                        label = { Text(trip.dayLabel(d)) },
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (phase is TripPhase.Ongoing && phase.day == day) {
                    item { AssistChip(onClick = {}, label = { Text("今日はこの日です") }) }
                }
                if (items.isEmpty()) {
                    item {
                        EmptyMessage("この日の予定はまだありません", "右下の「予定を追加」から、時刻・場所・メモを入れていきましょう。")
                    }
                }
                items(items, key = { it.id }) { item ->
                    ScheduleRow(item, onClick = { editing = item }, onMap = { openPlaceInMaps(context, item.place) })
                }
                val dayCost = trip.items.filter { it.day == day }.sumOf { it.cost }
                if (dayCost > 0) {
                    item {
                        Text(
                            "この日の費用 ${yen(dayCost)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("予定を追加") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        ItemEditDialog(
            trip = trip, item = null, initialDay = day,
            onSave = { item -> onUpdate { it.upsertItem(item) }; day = item.day; adding = false },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editing?.let { current ->
        ItemEditDialog(
            trip = trip, item = current, initialDay = day,
            onSave = { item -> onUpdate { it.upsertItem(item) }; editing = null },
            onDelete = { onUpdate { it.removeItem(current.id) }; editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ScheduleRow(item: ScheduleItem, onClick: () -> Unit, onMap: () -> Unit) {
    SoftCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.Start) {
                Text(
                    item.time.ifEmpty { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(item.kind.icon(), contentDescription = item.kind.label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.place.isNotEmpty()) {
                    Text(item.place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.memo.isNotEmpty()) {
                    Text(item.memo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.cost > 0) {
                    Text(yen(item.cost), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (item.place.isNotEmpty()) {
                IconButton(onClick = onMap, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = "地図で開く", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}
