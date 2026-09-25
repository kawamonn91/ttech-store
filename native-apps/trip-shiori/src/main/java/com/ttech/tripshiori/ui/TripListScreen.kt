package com.ttech.tripshiori.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.TripPhase
import com.ttech.tripshiori.domain.durationLabel
import com.ttech.tripshiori.domain.label
import com.ttech.tripshiori.domain.periodLabel
import com.ttech.tripshiori.domain.phase
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    trips: List<Trip>,
    today: LocalDate,
    onOpen: (Trip) -> Unit,
    onCreate: (Trip) -> Unit,
    onAddSample: () -> Unit,
    onImport: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("旅のしおり") },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "その他") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("しおりファイルを読み込む") }, onClick = { menu = false; onImport() })
                            DropdownMenuItem(text = { Text("サンプルのしおりを追加") }, onClick = { menu = false; onAddSample() })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (trips.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("新しいしおり") },
                )
            }
        },
    ) { padding ->
        if (trips.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyMessage(
                    title = "旅のしおりを作りましょう",
                    body = "日ごとの予定・持ち物・宿泊先・費用をまとめて、LINEやPDFで友だちや家族に共有できます。",
                ) {
                    Button(onClick = { creating = true }) { Text("しおりを作る") }
                    OutlinedButton(onClick = onAddSample) { Text("サンプルを見てみる") }
                    OutlinedButton(onClick = onImport) { Text("しおりファイルを読み込む") }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                items(trips, key = { it.id }) { trip -> TripCard(trip, today, onClick = { onOpen(trip) }) }
            }
        }
    }

    if (creating) {
        TripEditDialog(trip = null, today = today, onSave = { onCreate(it); creating = false }, onDismiss = { creating = false })
    }
}

@Composable
private fun TripCard(trip: Trip, today: LocalDate, onClick: () -> Unit) {
    val phase = trip.phase(today)
    SoftCard(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(trip.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    phase.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (phase) {
                        is TripPhase.Ongoing -> MaterialTheme.colorScheme.tertiary
                        is TripPhase.Upcoming -> MaterialTheme.colorScheme.primary
                        TripPhase.Finished -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                listOf(trip.destination, trip.periodLabel()).filter { it.isNotEmpty() }.joinToString("  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onClick, label = { Text(trip.durationLabel()) })
                AssistChip(onClick = onClick, label = { Text("予定 ${trip.items.size}件") })
                if (trip.packing.isNotEmpty()) AssistChip(onClick = onClick, label = { Text("持ち物 ${trip.packing.count { it.checked }}/${trip.packing.size}") })
            }
        }
    }
}
