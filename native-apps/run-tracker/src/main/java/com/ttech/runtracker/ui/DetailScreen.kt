package com.ttech.runtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ttech.common.share.shareBitmap
import com.ttech.common.share.shareTextFile
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.domain.Aggregates
import com.ttech.runtracker.domain.Calories
import com.ttech.runtracker.domain.RunSettings
import com.ttech.runtracker.domain.RunStats
import com.ttech.runtracker.domain.RunStatsCalculator
import com.ttech.runtracker.domain.RunSummary
import com.ttech.track.domain.Export
import com.ttech.track.domain.Format
import com.ttech.track.domain.LatLon
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.RouteSegments
import com.ttech.track.domain.TrackPoint
import com.ttech.track.map.RouteStyle
import com.ttech.track.ui.LineChart
import com.ttech.track.ui.RouteMapView
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(container: RunContainer, id: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = RunSettings())
    val allRuns by container.repository.runs.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    val summary by produceState<RunSummary?>(null, id, version) { value = container.repository.summary(id) }
    val points by produceState<List<TrackPoint>?>(null, id) { value = container.repository.track(id) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // グラフをなぞっている位置(地図に印を出す)
    var focus by remember { mutableStateOf<LatLon?>(null) }

    val s = summary
    val pts = points
    if (s == null || pts == null) {
        Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val stats = remember(pts, s.pauses, s.weightKg) { RunStatsCalculator.compute(pts, s.pauses, s.weightKg ?: Calories.DEFAULT_WEIGHT_KG) }
    val route = remember(pts) { RouteSegments.from(pts) }
    val mapStyle = if (settings.mapStyleDark) MapStyle.Dark else MapStyle.Light
    val records = remember(s, allRuns) { Aggregates.recordsSetBy(s, allRuns).toSet() }

    LaunchedEffect(s.id, s.startLabel) {
        // 出発地の名前が無ければ調べて保存する(取れなかったときは、次に開いたときにまた試す)
        if (s.startLabel == null) {
            container.places.name(s.startLat, s.startLon)?.let {
                container.repository.save(s.copy(startLabel = it))
                version++
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            TopAppBar(
                title = { Text(Format.date(s.startTimeMs)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "名前を変える") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "削除") }
                },
            )
        }
        item {
            // 指で動かせる地図。線は記録した点をそのままつないだもの(間引き・平滑化なし)
            Box(Modifier.fillMaxWidth().height(380.dp).background(MapDark)) {
                RouteMapView(route, container.tiles, mapStyle, RouteStyle.Orange, marker = focus)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.displayTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${Format.time(s.startTimeMs)} → ${Format.time(s.endTimeMs)}" + (s.startLabel?.let { " ・ $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (records.isNotEmpty()) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        records.sortedBy { it.meters }.forEach { Pill("自己ベスト ${it.label}", WarnAmber) }
                    }
                }
            }
        }
        item { Headline(s, stats) }
        if (stats.splits.isNotEmpty()) item { SplitsSection(stats) }
        item { ChartsSection(stats, pts, onFocus = { focus = it }) }
        if (stats.efforts.isNotEmpty()) item { EffortsSection(stats, records) }
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
                            if (result == null) message = "画像を作れませんでした" else shareBitmap(context, result.bitmap, "run-${s.id}.png", "ルートの画像を共有")
                        }
                    },
                ) { Text(if (busy) "画像を作っています…" else "ルートの画像を共有") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { shareTextFile(context, Export.gpx("${s.displayTitle} ${Format.dateTime(s.startTimeMs)}", pts, "T-tech ランニング記録"), "run-${s.id}.gpx", "application/gpx+xml", "GPXを書き出す") },
                ) { Text("GPXで書き出す(地図アプリ用)") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { shareTextFile(context, Export.csv(pts), "run-${s.id}.csv", "text/csv", "CSVを書き出す") },
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

        StatusBarScrim(Modifier.align(Alignment.TopStart))
    }

    if (renaming) {
        var text by remember { mutableStateOf(s.title ?: s.displayTitle) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("名前を変える") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, label = { Text("名前") }) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        container.repository.save(s.copy(title = text.trim().ifBlank { null }))
                        version++
                    }
                    renaming = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("キャンセル") } },
        )
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
private fun Headline(s: RunSummary, stats: RunStats) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Format.distanceKmNumber(stats.distanceM), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("km", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("時間", Format.clock(stats.movingMs), Modifier.weight(1f), sub = "走っていた時間")
            StatTile("ペース", Format.pace(stats.avgPaceSecPerKm), Modifier.weight(1f), sub = "平均")
            StatTile("経過時間", Format.clock(stats.elapsedMs), Modifier.weight(1f), sub = "停止を含む")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("獲得標高", if (stats.minAltitudeM == null) "-" else Format.meters(stats.elevationGainM), Modifier.weight(1f))
            StatTile(
                "消費カロリー", "${stats.caloriesKcal.roundToInt()} kcal", Modifier.weight(1f),
                sub = if (s.weightKg == null) "体重60kgで計算" else "体重 ${String.format(Locale.ROOT, "%.1f", s.weightKg)} kg",
            )
            StatTile("平均速度", String.format(Locale.ROOT, "%.1f km/h", stats.avgSpeedMps * 3.6), Modifier.weight(1f))
        }
    }
}

/** 1kmごとの区間。速い区間ほど棒が長い(ストラバと同じ見せ方) */
@Composable
private fun SplitsSection(stats: RunStats) {
    val splits = stats.splits
    val fastest = splits.filter { !it.isPartial }.minOfOrNull { it.paceSecPerKm } ?: splits.minOf { it.paceSecPerKm }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("1kmごとのペース")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("km", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("ペース", Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f))
                    Text("標高", Modifier.width(48.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                splits.forEach { sp ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (sp.isPartial) String.format(Locale.ROOT, "%.2f", sp.distanceM / 1000.0) else "${sp.index}",
                            Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(Format.paceNumber(sp.paceSecPerKm), Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        val fraction = (fastest / sp.paceSecPerKm).toFloat().coerceIn(0.2f, 1f)
                        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(
                                Modifier.fillMaxWidth(fraction).height(14.dp).clip(RoundedCornerShape(7.dp))
                                    .background(if (sp.paceSecPerKm == fastest && !sp.isPartial) AccentOrange else AccentOrange.copy(alpha = 0.5f)),
                            )
                        }
                        Text(
                            sp.elevationDiffM?.let { String.format(Locale.ROOT, "%+d", it.roundToInt()) } ?: "-",
                            Modifier.width(48.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartsSection(stats: RunStats, points: List<TrackPoint>, onFocus: (LatLon?) -> Unit) {
    if (points.size < 3 || stats.distanceM < 50) return
    val series = stats.series
    var cursor by remember { mutableStateOf<Int?>(null) }
    fun move(i: Int?) {
        cursor = i
        onFocus(i?.let { points[it].latLon })
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("ペース(分/km)")
        Text("グラフを長押ししてなぞると、地図にその位置が出ます。上にあるほど速いです", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LineChart(
            series.distKm, series.paceSecPerKm, color = AccentOrange, invertY = true, minSpanY = 60.0,
            yLabel = { Format.paceNumber(it) }, xLabel = { String.format(Locale.ROOT, "%.1fkm", it) },
            cursorIndex = cursor, onCursor = ::move,
        )
        cursor?.let { i ->
            Text(
                "${String.format(Locale.ROOT, "%.2f", series.distKm[i])} km ・ ${Format.pace(series.paceSecPerKm[i])} ・ ${Format.timeSeconds(points[i].timeMs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val alts = series.altitudeM
        if (alts != null) {
            SectionHeader("標高(m)")
            LineChart(series.distKm, alts, color = Color(0xFF2563EB), minSpanY = 20.0, yLabel = { "${it.toInt()}" }, xLabel = { String.format(Locale.ROOT, "%.1fkm", it) }, cursorIndex = cursor, onCursor = ::move)
        }
    }
}

/** 距離ごとの、この記録の中でいちばん速い区間 */
@Composable
private fun EffortsSection(stats: RunStats, records: Set<com.ttech.runtracker.domain.EffortDef>) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("この記録のベスト")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                stats.efforts.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.def.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (e.def in records) Pill("自己ベスト", WarnAmber)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Format.clock(e.timeMs), fontWeight = FontWeight.Bold)
                            Text(Format.pace(e.paceSecPerKm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataDetails(s: RunSummary, stats: RunStats) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("記録データ")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                LabeledRow("開始", Format.dateTime(s.startTimeMs))
                LabeledRow("終了", Format.dateTime(s.endTimeMs))
                LabeledRow("止まっていた時間", Format.duration(stats.stoppedMs))
                LabeledRow("一時停止", if (s.pauses.isEmpty()) "なし" else "${s.pauses.size}回")
                LabeledRow("記録した点", "${stats.pointCount}点(約1秒ごと)")
                LabeledRow("位置の精度(平均)", stats.avgAccuracyM?.let { "${String.format(Locale.ROOT, "%.1f", it)} m" } ?: "-")
                LabeledRow("GPSが途切れた回数", "${stats.gapCount}回")
                LabeledRow("標高(最低〜最高)", if (stats.minAltitudeM == null) "-" else "${stats.minAltitudeM.toInt()}〜${stats.maxAltitudeM!!.toInt()} m")
                LabeledRow("下り", if (stats.minAltitudeM == null) "-" else Format.meters(stats.elevationLossM))
            }
        }
        Text(
            "地図の線は、記録したGPSの点をそのままつないでいます(道路への補正や間引きはしていません)。GPSが途切れた区間と一時停止をはさんだ区間は、点線でつないでいます。消費カロリーは、体重・走る速さ・時間から推定した目安です。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
