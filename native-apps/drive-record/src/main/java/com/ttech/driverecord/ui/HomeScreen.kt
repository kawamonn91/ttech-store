package com.ttech.driverecord.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.driverecord.DriveContainer
import com.ttech.driverecord.DriveState
import com.ttech.driverecord.domain.DriveSettings
import com.ttech.driverecord.domain.DriveSummary
import com.ttech.driverecord.domain.LiveDrive
import com.ttech.driverecord.domain.Trigger
import com.ttech.driverecord.recording.DriveService
import com.ttech.track.domain.Format
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(container: DriveContainer, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drives by container.repository.drives.collectAsState()
    val settings by container.settings.settings.collectAsState(initial = DriveSettings())
    val live by DriveState.live.collectAsState()
    val car by DriveState.carConnected.collectAsState()
    val notice by DriveState.notice.collectAsState()
    val perm by rememberPermState()
    val actions = rememberPermissionActions { }
    var imageVersion by remember { mutableIntStateOf(0) }

    // 待機のサービスは、自動記録がオンで位置情報の権限があれば動かしておく
    LaunchedEffect(settings.autoRecord, perm.location) {
        if (settings.autoRecord && perm.location) DriveService.ensureRunning(context)
    }
    // 地図の画像が無い記録には、順番に作る(通信できないときは、次に開いたときに作り直す)
    LaunchedEffect(drives, settings.mapStyleDark) {
        for (d in drives.filter { !container.files.hasImage(it.id) }.take(30)) {
            if (container.images.ensure(d, container.repository.track(d.id), settings.mapStyleDark)) imageVersion++
        }
    }

    val grouped = remember(drives) { drives.groupBy { Format.dayKey(it.startTimeMs) }.toList() }
    val zone = remember { ZoneId.of("Asia/Tokyo") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ドライブ記録", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "設定") }
            }
        }
        item {
            val current = live
            if (current != null) {
                LivePanel(current, onStop = { DriveService.stopRecording(context) })
            } else {
                StatusPanel(
                    autoRecord = settings.autoRecord,
                    onAutoChange = { on -> scope.launch { container.settings.update { it.copy(autoRecord = on) }; if (!on) DriveService.shutdown(context) } },
                    carConnected = car,
                    perm = perm,
                    notice = notice,
                    actions = actions,
                    onManualStart = { DriveService.startManual(context) },
                )
            }
        }
        item {
            val month = YearMonth.now(zone)
            val thisMonth = drives.filter { YearMonth.from(Instant.ofEpochMilli(it.startTimeMs).atZone(zone)) == month }
            if (thisMonth.isNotEmpty()) MonthSummary(thisMonth)
        }
        if (drives.isEmpty()) {
            item {
                Text(
                    "まだドライブの記録がありません。Android Auto につなぐと、自動で記録を始めます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        for ((_, list) in grouped) {
            item(key = "h-${list.first().id}") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Format.date(list.first().startTimeMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${list.size}回 ・ ${Format.distance(list.sumOf { it.distanceM })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { d -> DriveCard(container, d, imageVersion, onClick = { onOpen(d.id) }) }
        }
    }
}

@Composable
private fun StatusPanel(
    autoRecord: Boolean,
    onAutoChange: (Boolean) -> Unit,
    carConnected: Boolean,
    perm: PermState,
    notice: String?,
    actions: PermissionActions,
    onManualStart: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Android Auto で自動記録", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (!autoRecord) "オフ(手動でだけ記録します)"
                        else if (carConnected) "Android Auto に接続中"
                        else "つながるのを待っています",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (autoRecord && carConnected) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoRecord, onCheckedChange = onAutoChange)
            }

            // 足りない権限・設定を、ひとつずつ案内する
            if (!perm.location) {
                Warning("位置情報の許可が必要です", "許可する", actions.requestLocation)
            } else if (!perm.gpsOn) {
                Warning("端末の位置情報(GPS)がオフです", "設定を開く", actions.openLocationSettings)
            } else if (autoRecord && !perm.background) {
                Warning("画面を閉じていても自動で記録するには、位置情報を「常に許可」にしてください", "設定を開く", actions.openAppSettings)
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(onClick = onManualStart, enabled = perm.canRecord, modifier = Modifier.fillMaxWidth()) { Text("いますぐ記録を始める") }
        }
    }
}

@Composable
private fun Warning(text: String, action: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, color = WarnAmber, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onClick) { Text(action) }
    }
}

/** 記録中の表示。速度を大きく、距離・時間・GPSの状態を並べる */
@Composable
private fun LivePanel(live: LiveDrive, onStop: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val gps by DriveState.gps.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("● 記録中", AccentRed)
                Pill(if (live.trigger == Trigger.ANDROID_AUTO) "Android Auto" else "手動", MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${Format.kmhNumber(live.speedMps)}", fontSize = 72.sp, fontWeight = FontWeight.Bold)
                Text("km/h", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 14.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("距離", Format.distance(live.distanceM))
                StatTile("経過時間", Format.clock(live.elapsedMs(now)))
                StatTile("最高速度", Format.speedKmh(live.maxSpeedMps))
            }
            Text(
                buildString {
                    append("GPS: ")
                    append(if (live.points == 0) "測位を待っています" else "精度 ${live.accuracyM?.let { "${it.toInt()}m" } ?: "-"}")
                    if (gps.satellitesVisible > 0) append(" ・ 衛星 ${gps.satellitesUsed}/${gps.satellitesVisible}")
                    append(" ・ 記録した点 ${live.points}")
                    if (live.rejected > 0) append("(除外 ${live.rejected})")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = AccentRed), modifier = Modifier.fillMaxWidth()) { Text("記録を終える") }
        }
    }
}

@Composable
private fun MonthSummary(list: List<DriveSummary>) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("今月の距離", Format.distance(list.sumOf { it.distanceM }))
            StatTile("回数", "${list.size}回")
            StatTile("運転時間", Format.duration(list.sumOf { it.durationMs }))
        }
    }
}

/** 一覧の1件。ストラバやグーグルマップのタイムラインのように、地図の画像を大きく見せる */
@Composable
private fun DriveCard(container: DriveContainer, d: DriveSummary, imageVersion: Int, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DriveThumb(container, d, imageVersion)
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${Format.time(d.startTimeMs)} → ${Format.time(d.endTimeMs)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (d.trigger == Trigger.ANDROID_AUTO) Pill("Android Auto", MaterialTheme.colorScheme.primary)
                }
                val from = d.startLabel ?: Format.coords(d.startLat, d.startLon)
                val to = d.endLabel ?: Format.coords(d.endLat, d.endLon)
                Text("$from → $to", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile("距離", Format.distance(d.distanceM), Modifier.weight(1.15f), valueSize = 17.sp)
                    StatTile("時間", Format.duration(d.durationMs), Modifier.weight(1.1f), valueSize = 17.sp)
                    StatTile("平均", Format.speedKmh(d.avgMovingSpeedMps), Modifier.weight(1f), valueSize = 17.sp)
                    StatTile("最高", Format.speedKmh(d.maxSpeedMps), Modifier.weight(1f), valueSize = 17.sp)
                }
            }
        }
    }
}
