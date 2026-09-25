package com.ttech.tripshiori.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.tripshiori.domain.Contact
import com.ttech.tripshiori.domain.Lodging
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.durationLabel
import com.ttech.tripshiori.domain.periodLabel
import com.ttech.tripshiori.domain.removeContact
import com.ttech.tripshiori.domain.removeLodging
import com.ttech.tripshiori.domain.upsertContact
import com.ttech.tripshiori.domain.upsertLodging

/** 電話アプリのダイヤル画面を開く(発信はしない。利用者が発信ボタンを押す) */
private fun dial(context: android.content.Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
fun InfoTab(trip: Trip, onUpdate: ((Trip) -> Trip) -> Unit, onEditTrip: () -> Unit) {
    val context = LocalContext.current
    var lodging by remember { mutableStateOf<Lodging?>(null) }
    var addLodging by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf<Contact?>(null) }
    var addContact by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("基本情報")
                        IconButton(onClick = onEditTrip) { Icon(Icons.Filled.Edit, contentDescription = "編集") }
                    }
                    Text(trip.title, style = MaterialTheme.typography.titleLarge)
                    if (trip.destination.isNotEmpty()) Text("行き先: ${trip.destination}", style = MaterialTheme.typography.bodyMedium)
                    Text("${trip.periodLabel()}(${trip.durationLabel()})", style = MaterialTheme.typography.bodyMedium)
                    if (trip.travelers.isNotEmpty()) Text("メンバー: ${trip.travelers.joinToString("・")}", style = MaterialTheme.typography.bodyMedium)
                    if (trip.notes.isNotEmpty()) Text(trip.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("宿泊先")
                TextButton(onClick = { addLodging = true }) { Text("追加") }
            }
        }
        if (trip.lodgings.isEmpty()) {
            item { Text("宿の名前・住所・予約番号を入れておくと、当日すぐに見られます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trip.lodgings.forEach { l ->
            item(key = l.id) {
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(l.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { lodging = l }) { Icon(Icons.Filled.Edit, contentDescription = "編集") }
                        }
                        if (l.address.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(l.address, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { openPlaceInMaps(context, l.address) }) { Text("地図") }
                            }
                        }
                        if (l.phone.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("電話: ${l.phone}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { dial(context, l.phone) }) { Icon(Icons.Filled.Call, contentDescription = "電話をかける") }
                            }
                        }
                        if (l.times.isNotEmpty()) Text(l.times, style = MaterialTheme.typography.bodyMedium)
                        if (l.reservation.isNotEmpty()) Text("予約番号: ${l.reservation}", style = MaterialTheme.typography.bodyMedium)
                        if (l.note.isNotEmpty()) Text(l.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("連絡先")
                TextButton(onClick = { addContact = true }) { Text("追加") }
            }
        }
        if (trip.contacts.isEmpty()) {
            item { Text("集合場所の担当・ホテル・同行者の電話番号などを入れておけます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trip.contacts.forEach { c ->
            item(key = c.id) {
                SoftCard {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            if (c.phone.isNotEmpty()) Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                            if (c.note.isNotEmpty()) Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.phone.isNotEmpty()) IconButton(onClick = { dial(context, c.phone) }) { Icon(Icons.Filled.Call, contentDescription = "電話をかける") }
                        IconButton(onClick = { contact = c }) { Icon(Icons.Filled.Edit, contentDescription = "編集") }
                    }
                }
            }
        }
    }

    if (addLodging) LodgingDialog(null, onSave = { l -> onUpdate { it.upsertLodging(l) }; addLodging = false }, onDelete = null, onDismiss = { addLodging = false })
    lodging?.let { current ->
        LodgingDialog(
            current,
            onSave = { l -> onUpdate { it.upsertLodging(l) }; lodging = null },
            onDelete = { onUpdate { it.removeLodging(current.id) }; lodging = null },
            onDismiss = { lodging = null },
        )
    }
    if (addContact) ContactDialog(null, onSave = { c -> onUpdate { it.upsertContact(c) }; addContact = false }, onDelete = null, onDismiss = { addContact = false })
    contact?.let { current ->
        ContactDialog(
            current,
            onSave = { c -> onUpdate { it.upsertContact(c) }; contact = null },
            onDelete = { onUpdate { it.removeContact(current.id) }; contact = null },
            onDismiss = { contact = null },
        )
    }
}
