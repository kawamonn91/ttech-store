package com.ttech.roomcheckin.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.common.BitMatrix
import com.ttech.roomcheckin.data.RoomStore
import com.ttech.roomcheckin.domain.Room
import com.ttech.roomcheckin.domain.addRoom
import com.ttech.roomcheckin.domain.checkIn
import com.ttech.roomcheckin.domain.checkOut
import com.ttech.roomcheckin.domain.encodeQr
import com.ttech.roomcheckin.domain.qrContent
import com.ttech.roomcheckin.domain.removeRoom
import com.ttech.roomcheckin.domain.statusLabel
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RoomCheckinScreen() {
    val context = LocalContext.current
    val store = remember { RoomStore(context) }
    val scope = rememberCoroutineScope()
    val rooms by store.rooms.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var qrRoomId by remember { mutableStateOf<String?>(null) }

    fun addRoom() {
        if (name.isBlank()) return
        val room = Room(id = UUID.randomUUID().toString(), name = name.trim())
        scope.launch { store.update { it.addRoom(room) } }
        name = ""
    }

    fun removeRoom(id: String) {
        scope.launch { store.update { it.removeRoom(id) } }
        if (qrRoomId == id) qrRoomId = null
    }

    Scaffold(topBar = { TopAppBar(title = { Text("会議室チェックイン") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("会議室・座席名を追加") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = ::addRoom, enabled = name.isNotBlank()) { Text("追加") }
                    }
                }
            }

            if (rooms.isEmpty()) {
                item {
                    Text(
                        "まだ会議室・座席が登録されていません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }

            items(rooms, key = Room::id) { room ->
                RoomCard(
                    room = room,
                    showQr = qrRoomId == room.id,
                    onToggleQr = { qrRoomId = if (qrRoomId == room.id) null else room.id },
                    onRemove = { removeRoom(room.id) },
                    onCheckIn = { who -> scope.launch { store.update { it.checkIn(room.id, who, System.currentTimeMillis()) } } },
                    onCheckOut = { scope.launch { store.update { it.checkOut(room.id) } } },
                )
            }

            item {
                Text(
                    "※ QRコードはドアや座席への掲示用です。利用者はこのアプリの画面から手動でチェックイン/アウトを行います(カメラでのスキャンは行いません)。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: Room,
    showQr: Boolean,
    onToggleQr: () -> Unit,
    onRemove: () -> Unit,
    onCheckIn: (String) -> Unit,
    onCheckOut: () -> Unit,
) {
    var who by remember(room.id) { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        room.statusLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (room.occupiedBy != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onToggleQr) { Text(if (showQr) "QRを閉じる" else "QR表示") }
                TextButton(onClick = onRemove) { Text("削除") }
            }

            if (showQr) {
                val bitmap = remember(room.name) { encodeQr(room.qrContent())?.toBitmap() }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "${room.name}のQRコード",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                    )
                }
            }

            if (room.occupiedBy != null) {
                FilledTonalButton(onClick = onCheckOut, modifier = Modifier.fillMaxWidth()) { Text("チェックアウト") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = who,
                        onValueChange = { who = it },
                        label = { Text("利用者名(任意)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { onCheckIn(who); who = "" }) { Text("チェックイン") }
                }
            }
        }
    }
}

private fun BitMatrix.toBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}
