package com.ttech.travelwishlist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.Status
import com.ttech.travelwishlist.domain.monthsLabel
import com.ttech.travelwishlist.domain.pickRandom
import com.ttech.travelwishlist.domain.recommendedThisMonth
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun DiscoverScreen(
    places: List<Place>,
    currentMonth: Int,
    contentPadding: PaddingValues,
    onOpen: (Place) -> Unit,
    onSetStatus: (Place, Status) -> Unit,
    onAdd: () -> Unit,
) {
    val recommended = recommendedThisMonth(places, currentMonth)
    val candidates = places.filter { it.status != Status.VISITED }

    // 「ランダムに決める」: 名前が次々に切り替わってから、抽選で決まった場所で止まる
    var spinKey by rememberSaveable { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf<String?>(null) }
    var resultId by rememberSaveable { mutableStateOf<String?>(null) }
    var spinning by remember { mutableStateOf(false) }
    val result = places.firstOrNull { it.id == resultId }

    LaunchedEffect(spinKey) {
        if (spinKey == 0) return@LaunchedEffect
        val winner = pickRandom(candidates, currentMonth, Random.Default)
        if (winner == null) {
            resultId = null
            return@LaunchedEffect
        }
        spinning = true
        resultId = null
        val names = candidates.map { it.name }
        for (i in 0 until 16) {
            shown = names.random()
            delay(70L + i * 12L)
        }
        shown = winner.name
        resultId = winner.id
        spinning = false
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item { SectionTitle("次どこ行く?") }
        item {
            SoftCard {
                Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        candidates.isEmpty() -> {
                            Text("まだ行っていない場所がありません", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Text("行きたい場所を追加すると、ここから抽選できます。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Button(onClick = onAdd) { Text("行きたい場所を追加") }
                        }
                        shown == null -> {
                            Text("迷ったら、運まかせ", style = MaterialTheme.typography.titleMedium)
                            Text("行きたい度が高い場所ほど、選ばれやすくなります。今月が行きたい時期の場所は、さらに選ばれやすくなります。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Button(onClick = { spinKey++ }) { Text("ランダムに決める") }
                        }
                        else -> {
                            Text(shown ?: "", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = if (spinning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            if (!spinning && result != null) {
                                val summary = result.summaryLine()
                                if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Stars(result.priority, size = 20.dp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onOpen(result) }) { Text("詳しく見る") }
                                    if (result.status == Status.WANT) Button(onClick = { onSetStatus(result, Status.PLANNING) }) { Text("計画中にする") }
                                }
                            }
                            OutlinedButton(onClick = { spinKey++ }, enabled = !spinning) { Text(if (spinning) "抽選中…" else "もう一度") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("${currentMonth}月におすすめ", Modifier.padding(top = 8.dp)) }
        if (recommended.isEmpty()) {
            item {
                Text(
                    "今月が「行きたい時期」に入っている場所は、まだありません。場所を追加するときに時期を入れておくと、ちょうどよい季節にここへ出てきます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(recommended, key = { it.id }) { p ->
            SoftCard(onClick = { onOpen(p) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        val summary = p.summaryLine()
                        if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(monthsLabel(p.months), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Stars(p.priority)
                }
            }
        }
    }
}
