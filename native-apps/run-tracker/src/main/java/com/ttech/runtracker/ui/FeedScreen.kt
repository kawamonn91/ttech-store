package com.ttech.runtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.domain.Aggregates
import com.ttech.runtracker.domain.RunSettings
import com.ttech.runtracker.domain.RunSummary
import com.ttech.track.domain.Format
import java.time.Instant
import java.time.LocalDate

/** 記録の一覧。ストラバのように、上に今週の走行、下に地図つきのカードを新しい順に並べる */
@Composable
fun FeedScreen(container: RunContainer, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val runs by container.repository.runs.collectAsState()
    val settings by container.settings.settings.collectAsState(initial = RunSettings())
    var imageVersion by remember { mutableIntStateOf(0) }

    // 地図の画像が無い記録には、順番に作る(通信できないときは、次に開いたときに作り直す)
    LaunchedEffect(runs, settings.mapStyleDark) {
        for (r in runs.filter { !container.files.hasImage(it.id) }.take(30)) {
            if (container.images.ensure(r, container.repository.track(r.id), settings.mapStyleDark)) imageVersion++
        }
    }
    val records = remember(runs) { runs.associate { it.id to Aggregates.recordsSetBy(it, runs) } }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ランニング記録", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "設定") }
            }
        }
        item { WeekCard(runs) }
        if (runs.isEmpty()) {
            item {
                Text(
                    "まだ記録がありません。「計測」タブの「スタート」から走り始めると、ここに地図つきで並びます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(runs, key = { it.id }) { r -> RunCard(container, r, records[r.id].orEmpty().size, imageVersion, onClick = { onOpen(r.id) }) }
    }
}

/** 今週の走行。月曜から日曜までの、日ごとの距離を棒で見せる */
@Composable
private fun WeekCard(runs: List<RunSummary>) {
    val now = System.currentTimeMillis()
    val week = remember(runs) { Aggregates.totals(Aggregates.inWeek(runs, now)) }
    val days = remember(runs) { Aggregates.daysOfWeek(runs, now) }
    val today = remember { LocalDate.now(Aggregates.JST).dayOfWeek.value - 1 }
    val monday = remember { Aggregates.startOfWeek(now) }
    val label = "${Format.dateShort(monday).substringBefore('(')}〜${Format.dateShort(monday + 6 * 86_400_000L).substringBefore('(')}"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("今週", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("距離", Format.distance(week.distanceM), Modifier.weight(1.3f))
                StatTile("時間", Format.duration(week.movingMs), Modifier.weight(1.2f))
                StatTile("回数", "${week.count}回", Modifier.weight(0.8f))
            }
            val max = (days.maxOrNull() ?: 0.0).coerceAtLeast(1000.0)
            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, d ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        val fraction = if (d > 0) (d / max).toFloat().coerceIn(0.06f, 1f) else 0.06f
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(4.dp))
                                .background(if (d > 0) AccentOrange else MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("月", "火", "水", "木", "金", "土", "日").forEachIndexed { i, name ->
                    Text(
                        name,
                        Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (i == today) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == today) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 一覧の1件。名前・日時・場所、地図の画像、距離・ペース・時間の順に見せる */
@Composable
private fun RunCard(container: RunContainer, r: RunSummary, recordCount: Int, imageVersion: Int, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(AccentOrange), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(r.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull("${Format.dateShort(r.startTimeMs)} ${Format.time(r.startTimeMs)}", r.startLabel).joinToString(" ・ "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (recordCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = WarnAmber, modifier = Modifier.size(18.dp))
                        Text("自己ベスト", style = MaterialTheme.typography.labelSmall, color = WarnAmber)
                    }
                }
            }
            RunThumb(container, r, imageVersion)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("距離", Format.distance(r.distanceM), Modifier.weight(1.1f), valueSize = 19.sp)
                StatTile("ペース", Format.pace(r.avgPaceSecPerKm), Modifier.weight(1.2f), valueSize = 19.sp)
                StatTile("時間", Format.duration(r.movingMs), Modifier.weight(1f), valueSize = 19.sp)
            }
        }
    }
}
