package com.ttech.driverecord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ttech.common.share.shareBitmap
import com.ttech.common.share.shareTextFile
import com.ttech.driverecord.DriveContainer
import com.ttech.driverecord.domain.DriveEvent
import com.ttech.driverecord.domain.DriveSettings
import com.ttech.driverecord.domain.DriveStats
import com.ttech.driverecord.domain.DriveStatsCalculator
import com.ttech.driverecord.domain.DriveSummary
import com.ttech.driverecord.domain.EventType
import com.ttech.driverecord.domain.Trigger
import com.ttech.track.domain.Export
import com.ttech.track.domain.Format
import com.ttech.track.domain.LatLon
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.RouteSegments
import com.ttech.track.domain.TrackPoint
import com.ttech.track.map.RouteStyle
import com.ttech.track.ui.LineChart
import com.ttech.track.ui.RouteMapView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(container: DriveContainer, id: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = DriveSettings())
    val summary by produceState<DriveSummary?>(null, id) { value = container.repository.summary(id) }
    val points by produceState<List<TrackPoint>?>(null, id) { value = container.repository.track(id) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // グラフをなぞっている位置(地図に印を出す)や、イベントを選んだ位置
    var focus by remember { mutableStateOf<LatLon?>(null) }

    val s = summary
    val pts = points
    if (s == null || pts == null) {
        Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val stats = remember(pts) { DriveStatsCalculator.compute(pts) }
    val route = remember(pts) { RouteSegments.from(pts) }
    val mapStyle = if (settings.mapStyleDark) MapStyle.Dark else MapStyle.Light

    LaunchedEffect(s.id, s.startLabel, s.endLabel) {
        // 出発地・到着地の名前が足りなければ、足りないほうだけ調べて保存する(取れなかったときは、次に開いたときにまた試す)
        if (s.startLabel == null || s.endLabel == null) {
            val start = s.startLabel ?: container.places.name(s.startLat, s.startLon)
            val end = s.endLabel ?: container.places.name(s.endLat, s.endLon)
            if (start != s.startLabel || end != s.endLabel) container.repository.save(s.copy(startLabel = start, endLabel = end))
        }
    }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
        item {
            TopAppBar(
                title = { Text(Format.date(s.startTimeMs)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "削除") } },
            )
        }
        item {
            // 指で動かせる地図。線は記録した点をそのままつないだもの(間引き・平滑化なし)
            Box(Modifier.fillMaxWidth().height(380.dp).background(MapDark)) {
                RouteMapView(route, container.tiles, mapStyle, RouteStyle.Red, marker = focus)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${Format.time(s.startTimeMs)} → ${Format.time(s.endTimeMs)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (s.trigger == Trigger.ANDROID_AUTO) Pill("Android Auto", MaterialTheme.colorScheme.primary, Modifier.align(Alignment.CenterVertically))
                }
                Text("出発: ${s.startLabel ?: Format.coords(s.startLat, s.startLon)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("到着: ${s.endLabel ?: Format.coords(s.endLat, s.endLon)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Headline(stats) }
        item { ChartsSection(stats, pts, onFocus = { focus = it }) }
        item { SpeedBands(stats) }
        if (stats.events.isNotEmpty()) item { EventsSection(stats.events, onSelect = { focus = LatLon(it.lat, it.lon) }) }
        if (stats.stops.isNotEmpty()) item {
            SectionHeaderPadded("停止(信号待ちなど)")
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    stats.stops.forEach { st ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Format.timeSeconds(st.startMs), style = MaterialTheme.typography.bodyMedium)
                            Text(Format.duration(st.durationMs), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        item { DataDetails(s, stats) }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = container.images.render(s, pts, settings.mapStyleDark)
                            busy = false
                            if (result == null) message = "画像を作れませんでした" else shareBitmap(context, result.bitmap, "drive-${s.id}.png", "ルートの画像を共有")
                        }
                    },
                ) { Text(if (busy) "画像を作っています…" else "ルートの画像を共有") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { shareTextFile(context, Export.gpx("ドライブ ${Format.dateTime(s.startTimeMs)}", pts, "T-tech ドライブ記録"), "drive-${s.id}.gpx", "application/gpx+xml", "GPXを書き出す") },
                ) { Text("GPXで書き出す(地図アプリ用)") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { shareTextFile(context, Export.csv(pts), "drive-${s.id}.csv", "text/csv", "CSVを書き出す") },
                ) { Text("CSVで書き出す(全データ)") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            busy = true
                            val ok = container.images.regenerate(s, pts, settings.mapStyleDark)
                            busy = false
                            message = if (ok) "地図の画像を作り直しました" else "地図を取得できませんでした。通信できる場所でもう一度お試しください"
                        }
                    },
                ) { Text("地図の画像を作り直す") }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "この記録を削除",
            message = "ルート・走行データ・地図の画像がすべて削除されます。元には戻せません(必要なら先にGPX/CSVで書き出してください)。",
            confirmLabel = "削除する",
            danger = true,
            onConfirm = { scope.launch { container.repository.delete(s.id); onDeleted() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SectionHeaderPadded(text: String) = SectionHeader(text, Modifier.padding(horizontal = 16.dp))

@Composable
private fun Headline(stats: DriveStats) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Format.distanceKmNumber(stats.distanceM), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("km", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("時間", Format.duration(stats.durationMs), Modifier.weight(1f))
            StatTile("平均速度", Format.speedKmh(stats.avgMovingSpeedMps), Modifier.weight(1f), sub = "走行中")
            StatTile("最高速度", Format.speedKmh(stats.maxSpeedMps), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("動いた時間", Format.duration(stats.movingMs), Modifier.weight(1f))
            StatTile("停止", Format.duration(stats.stoppedMs), Modifier.weight(1f), sub = "${stats.stops.size}回")
            StatTile("標高", if (stats.minAltitudeM == null) "-" else "↑${Format.meters(stats.elevationGainM)}", Modifier.weight(1f), sub = if (stats.minAltitudeM == null) null else "↓${Format.meters(stats.elevationLossM)}")
        }
    }
}

@Composable
private fun ChartsSection(stats: DriveStats, points: List<TrackPoint>, onFocus: (LatLon?) -> Unit) {
    if (points.size < 3) return
    val t0 = points.first().timeMs
    val xs = remember(points) { DoubleArray(points.size) { (points[it].timeMs - t0) / 60000.0 } } // 分
    val speeds = remember(points) { DriveStatsCalculator.pointSpeeds(points).map { it * 3.6 }.toDoubleArray() }
    var cursor by remember { mutableStateOf<Int?>(null) }
    fun move(i: Int?) {
        cursor = i
        onFocus(i?.let { points[it].latLon })
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("速度(km/h)")
        Text("グラフを長押ししてなぞると、地図にその位置が出ます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LineChart(xs, speeds, color = AccentRed, yLabel = { "${it.toInt()}" }, xLabel = { "${it.toInt()}分" }, minSpanY = 20.0, zeroFloor = true, cursorIndex = cursor, onCursor = ::move)
        cursor?.let { i -> Text("${Format.timeSeconds(points[i].timeMs)} ・ ${speeds[i].toInt()} km/h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (points.any { it.altitude != null }) {
            SectionHeader("標高(m)")
            val alts = remember(points) {
                var last = points.firstOrNull { it.altitude != null }?.altitude ?: 0.0
                DoubleArray(points.size) { i -> points[i].altitude?.also { last = it } ?: last }
            }
            LineChart(xs, alts, color = Color(0xFF2563EB), yLabel = { "${it.toInt()}" }, xLabel = { "${it.toInt()}分" }, minSpanY = 20.0, cursorIndex = cursor, onCursor = ::move)
        }
    }
}

@Composable
private fun SpeedBands(stats: DriveStats) {
    val total = stats.speedBandMs.sum().coerceAtLeast(1L)
    val labels = listOf("〜10", "10〜30", "30〜50", "50〜80", "80〜100", "100〜")
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("速度帯ごとの時間(km/h)")
        stats.speedBandMs.forEachIndexed { i, ms ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(labels[i], style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(ms.toFloat() / total).height(14.dp).clip(RoundedCornerShape(7.dp)).background(AccentRed))
                }
                Text(Format.duration(ms), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(76.dp).padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun EventsSection(events: List<DriveEvent>, onSelect: (DriveEvent) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("急な操作")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "GPSの速度と向きの変化から求めた目安です(急ブレーキ・急加速は毎秒約3m以上の変化、急ハンドルは横方向の加速度が約3.5m/s²以上)。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                events.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).then(Modifier),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.type.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = eventColor(e.type))
                            Text("${Format.timeSeconds(e.timeMs)} ・ ${Format.speedKmh(e.speedMps)} ・ ${"%.1f".format(e.value)} ${e.type.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onSelect(e) }) { Text("地図で見る") }
                    }
                }
            }
        }
    }
}

private fun eventColor(type: EventType): Color = when (type) {
    EventType.HardBrake -> AccentRed
    EventType.HardAccel -> WarnAmber
    EventType.SharpCorner -> Color(0xFF7C3AED)
}

@Composable
private fun DataDetails(s: DriveSummary, stats: DriveStats) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("記録データ")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                LabeledRow("開始", Format.dateTime(s.startTimeMs))
                LabeledRow("終了", Format.dateTime(s.endTimeMs))
                LabeledRow("記録した点", "${stats.pointCount}点(約1秒ごと)")
                LabeledRow("位置の精度(平均)", stats.avgAccuracyM?.let { "${"%.1f".format(it)} m" } ?: "-")
                LabeledRow("GPSが途切れた回数", "${stats.gapCount}回")
                LabeledRow("最高速度", "${"%.1f".format(stats.maxSpeedMps * 3.6)} km/h")
                LabeledRow("標高(最低〜最高)", if (stats.minAltitudeM == null) "-" else "${stats.minAltitudeM.toInt()}〜${stats.maxAltitudeM!!.toInt()} m")
                LabeledRow("急ブレーキ / 急加速 / 急ハンドル", "${stats.hardBrakeCount} / ${stats.hardAccelCount} / ${stats.sharpCornerCount}")
                LabeledRow("記録のきっかけ", if (s.trigger == Trigger.ANDROID_AUTO) "Android Auto" else "手動")
            }
        }
        Text(
            "地図の線は、記録したGPSの点をそのままつないでいます(道路への補正や間引きはしていません)。トンネルなどでGPSが途切れた区間は、点線でつないでいます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
