package com.ttech.travelwishlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.travelwishlist.domain.Category
import com.ttech.travelwishlist.domain.Japan
import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.Status
import com.ttech.travelwishlist.domain.computeStats
import com.ttech.travelwishlist.domain.parseDateOrNull
import com.ttech.travelwishlist.domain.stars
import com.ttech.travelwishlist.domain.yen
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(places: List<Place>, contentPadding: PaddingValues, onOpen: (Place) -> Unit) {
    val stats = computeStats(places)
    val memories = places.filter { it.status == Status.VISITED }.sortedByDescending { it.visitedDate }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Counter("行った", stats.visited, Modifier.weight(1f))
                Counter("計画中", stats.planning, Modifier.weight(1f))
                Counter("行きたい", stats.want, Modifier.weight(1f))
            }
        }
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("都道府県の制覇")
                        Text(
                            "${stats.visitedPrefectures.size} / ${Japan.PREFECTURES.size}(${String.format(Locale.US, "%.0f", stats.prefectureRate * 100)}%)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    LinearProgressIndicator(progress = { stats.prefectureRate.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legend("行った", MaterialTheme.colorScheme.primary)
                        Legend("行きたい・計画中", MaterialTheme.colorScheme.tertiaryContainer)
                    }
                    for ((area, prefectures) in Japan.REGIONS) {
                        Text(area, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (p in prefectures) {
                                val visited = p in stats.visitedPrefectures
                                val wanted = p in stats.wantedPrefectures
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        visited -> MaterialTheme.colorScheme.primary
                                        wanted -> MaterialTheme.colorScheme.tertiaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                ) {
                                    Text(
                                        Japan.shortName(p),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when {
                                            visited -> MaterialTheme.colorScheme.onPrimary
                                            wanted -> MaterialTheme.colorScheme.onTertiaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (stats.visitedOverseas > 0) {
                        Text("海外: ${stats.visitedOverseas}か国・地域に行きました", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (stats.budgetToGo > 0 || stats.averageRating != null) {
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (stats.budgetToGo > 0) Text("これから行く場所の予算の合計 ${yen(stats.budgetToGo)}", style = MaterialTheme.typography.bodyLarge)
                        stats.averageRating?.let { Text("行った場所の満足度の平均 ${String.format(Locale.US, "%.1f", it)} / 5", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
        if (stats.byCategory.isNotEmpty()) {
            item { SectionTitle("カテゴリ別", Modifier.padding(top = 4.dp)) }
            items(stats.byCategory.entries.sortedByDescending { it.value }.toList(), key = { it.key.name }) { (category: Category, count: Int) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category.label, style = MaterialTheme.typography.bodyLarge)
                        Text("${count}件", style = MaterialTheme.typography.bodyLarge)
                    }
                    LinearProgressIndicator(progress = { count.toFloat() / stats.total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item { SectionTitle("旅の思い出", Modifier.padding(top = 4.dp)) }
        if (memories.isEmpty()) {
            item {
                Text(
                    "行った場所は、詳細画面の「行った!」で記録できます。日付・満足度・感想が、ここに思い出として並びます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(memories, key = { it.id }) { p ->
            SoftCard(onClick = { onOpen(p) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        parseDateOrNull(p.visitedDate)?.let { Text("${it.year}/${it.monthValue}/${it.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (p.rating > 0) Text(stars(p.rating), color = StarColor)
                    if (p.impression.isNotEmpty()) Text(p.impression, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, modifier: Modifier) {
    SoftCard(modifier) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
