package com.ttech.runtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.domain.Aggregates
import com.ttech.runtracker.domain.PersonalBest
import com.ttech.runtracker.domain.Totals
import com.ttech.runtracker.domain.WeekBucket
import com.ttech.track.domain.Format

/** 成績。今週・今月・今年・ぜんぶの合計、週ごとの距離、距離ごとの自己ベスト */
@Composable
fun ProgressScreen(container: RunContainer, onOpen: (String) -> Unit) {
    val runs by container.repository.runs.collectAsState()
    val now = System.currentTimeMillis()
    val week = remember(runs) { Aggregates.totals(Aggregates.inWeek(runs, now)) }
    val month = remember(runs) { Aggregates.totals(Aggregates.inMonth(runs, now)) }
    val year = remember(runs) { Aggregates.totals(Aggregates.inYear(runs, now)) }
    val all = remember(runs) { Aggregates.totals(runs) }
    val weekly = remember(runs) { Aggregates.weekly(runs, now, 12) }
    val bests = remember(runs) { Aggregates.personalBests(runs) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("成績", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalsCard("今週", week, Modifier.weight(1f))
                TotalsCard("今月", month, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalsCard("今年", year, Modifier.weight(1f))
                TotalsCard("これまでの合計", all, Modifier.weight(1f))
            }
        }

        SectionHeader("週ごとの距離(直近12週)")
        Card(Modifier.fillMaxWidth()) { Box(Modifier.padding(16.dp)) { WeeklyBars(weekly) } }

        SectionHeader("自己ベスト")
        if (bests.isEmpty()) {
            Text(
                "1km以上走ると、1km・5kmなど距離ごとのいちばん速い記録がここに並びます。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    bests.forEachIndexed { i, b ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        BestRow(b, onClick = { onOpen(b.runId) })
                    }
                }
            }
            Text(
                "自己ベストは、どこから走り始めてもいちばん速く走れた区間の時間です(1km・5kmなどの大会の記録とは、測り方が違います)。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun TotalsCard(label: String, t: Totals, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.distance(t.distanceM), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text("${t.count}回 ・ ${Format.duration(t.movingMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                if (t.count > 0) "平均 ${Format.pace(t.avgPaceSecPerKm)}" else "-",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 週ごとの距離の棒グラフ。今週だけ濃い色にする */
@Composable
private fun WeeklyBars(weeks: List<WeekBucket>) {
    val max = (weeks.maxOfOrNull { it.distanceM } ?: 0.0).coerceAtLeast(1000.0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("最大 ${Format.distance(max)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            weeks.forEachIndexed { i, w ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    val fraction = if (w.distanceM > 0) (w.distanceM / max).toFloat().coerceIn(0.04f, 1f) else 0.03f
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    w.distanceM <= 0 -> MaterialTheme.colorScheme.surfaceVariant
                                    i == weeks.lastIndex -> AccentOrange
                                    else -> AccentOrange.copy(alpha = 0.45f)
                                },
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            weeks.forEachIndexed { i, w ->
                // 4週ごとと、今週の日付(週の月曜)だけ出す
                val show = i == weeks.lastIndex || (weeks.lastIndex - i) % 4 == 0 && weeks.lastIndex - i >= 3
                Text(
                    if (show) Format.dateShort(w.startMs).substringBefore('(') else "",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val thisWeek = weeks.lastOrNull()
        if (thisWeek != null) Text("今週 ${Format.distance(thisWeek.distanceM)}(${thisWeek.count}回)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BestRow(b: PersonalBest, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = WarnAmber)
            Column {
                Text(b.def.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(Format.date(b.startTimeMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.clock(b.timeMs), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(Format.pace(b.paceSecPerKm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
