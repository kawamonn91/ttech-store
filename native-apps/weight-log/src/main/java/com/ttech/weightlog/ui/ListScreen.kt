package com.ttech.weightlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.weightlog.domain.Body
import com.ttech.weightlog.domain.Profile
import com.ttech.weightlog.domain.Stats
import com.ttech.weightlog.domain.WeightEntry
import com.ttech.weightlog.domain.formatKg
import com.ttech.weightlog.domain.formatKgSigned
import com.ttech.weightlog.domain.sortedByDateDesc

@Composable
fun ListScreen(
    entries: List<WeightEntry>,
    stats: Stats,
    profile: Profile,
    contentPadding: PaddingValues,
    onOpen: (WeightEntry) -> Unit,
    onAdd: () -> Unit,
    onAddSample: () -> Unit,
    onImport: () -> Unit,
) {
    if (entries.isEmpty()) {
        Column(Modifier.padding(contentPadding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyMessage(
                title = "今日の体重を記録しましょう",
                body = "毎日つけるだけで、推移のグラフと、目標までの進み具合が分かります。",
            ) {
                Button(onClick = onAdd) { Text("体重を記録") }
                OutlinedButton(onClick = onAddSample) { Text("サンプルを見てみる") }
                OutlinedButton(onClick = onImport) { Text("バックアップを読み込む") }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val latest = stats.latest
        if (latest != null) item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text("最新の体重(${latest.date})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatKg(latest.weightKg), style = MaterialTheme.typography.displaySmall)
                        }
                        stats.streakDays.takeIf { it > 0 }?.let { Text("🔥 ${it}日連続", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
        item { WeightChart(entries, profile.goalWeightKg, androidx.compose.ui.Modifier.padding(vertical = 4.dp)) }
        if (latest != null) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                stats.changeFromStart?.let { StatTile("開始から", formatKgSigned(it), valueColor = changeColor(it)) }
                stats.changeLast7Days?.let { StatTile("直近7日", formatKgSigned(it), valueColor = changeColor(it)) }
                profile.heightCm?.let { h ->
                    Body.bmi(latest.weightKg, h)?.let { bmi -> StatTile("BMI", String.format(java.util.Locale.US, "%.1f", bmi)) }
                }
            }
        }
        profile.goalWeightKg?.let { goal ->
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("目標 ${formatKg(goal)}", style = MaterialTheme.typography.titleMedium)
                            stats.remainingToGoal?.let { r ->
                                Text(
                                    if (r > 0) "あと ${formatKg(r)}" else "達成しました!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (r > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        stats.progressPercent?.let { p ->
                            androidx.compose.material3.LinearProgressIndicator(progress = { (p / 100.0).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        item { SectionTitle("記録一覧(${entries.size}件)", Modifier.padding(top = 8.dp)) }
        items(sortedByDateDesc(entries), key = { it.id }) { e -> EntryRow(e, onClick = { onOpen(e) }) }
    }
}

@Composable
private fun changeColor(value: Double) = if (value <= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

@Composable
private fun EntryRow(entry: WeightEntry, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.date, style = MaterialTheme.typography.titleMedium)
                if (entry.memo.isNotEmpty()) Text(entry.memo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatKg(entry.weightKg), style = MaterialTheme.typography.titleMedium)
                entry.bodyFatPercent?.let { Text("体脂肪 ${it}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
