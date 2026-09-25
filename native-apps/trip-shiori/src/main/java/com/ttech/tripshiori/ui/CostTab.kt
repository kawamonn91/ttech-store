package com.ttech.tripshiori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.costByDay
import com.ttech.tripshiori.domain.costByKind
import com.ttech.tripshiori.domain.costPerPerson
import com.ttech.tripshiori.domain.dayLabel
import com.ttech.tripshiori.domain.totalCost
import com.ttech.tripshiori.domain.yen

@Composable
fun CostTab(trip: Trip) {
    val total = trip.totalCost()
    val perPerson = trip.costPerPerson()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("費用の合計", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(yen(total), style = MaterialTheme.typography.headlineLarge)
                    if (perPerson != null) {
                        Text("1人あたり ${yen(perPerson)}(${trip.travelers.size}人で割って、端数は切り上げ)", style = MaterialTheme.typography.bodyMedium)
                    } else if (total > 0) {
                        Text("メンバーを2人以上入れると、1人あたりの金額も出ます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (total == 0) {
            item { EmptyMessage("費用はまだありません", "日程の予定に費用を入れると、ここで種類別・日別に集計されます。") }
            return@LazyColumn
        }
        item { SectionTitle("種類別") }
        val byKind = trip.costByKind().entries.sortedByDescending { it.value }
        byKind.forEach { (kind, amount) ->
            item(key = "kind-${kind.name}") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.label, style = MaterialTheme.typography.bodyLarge)
                        Text(yen(amount), style = MaterialTheme.typography.bodyLarge)
                    }
                    LinearProgressIndicator(progress = { amount.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item { SectionTitle("日別", Modifier.padding(top = 8.dp)) }
        trip.costByDay().forEachIndexed { day, amount ->
            item(key = "day-$day") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(trip.dayLabel(day), style = MaterialTheme.typography.bodyLarge)
                    Text(if (amount > 0) yen(amount) else "—", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
