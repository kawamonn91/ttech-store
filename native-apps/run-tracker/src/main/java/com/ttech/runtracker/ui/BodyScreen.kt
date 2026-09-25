package com.ttech.runtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.domain.Body
import com.ttech.runtracker.domain.WeightEntry
import com.ttech.track.domain.Format
import com.ttech.track.ui.LineChart
import java.util.Locale
import kotlinx.coroutines.launch

/** 身体の記録。身長と体重を入れると、BMI・標準体重を出し、ランの消費カロリーの推定にも使う */
@Composable
fun BodyScreen(container: RunContainer) {
    val scope = rememberCoroutineScope()
    val entries by container.body.entries.collectAsState()
    val height by container.settings.heightCm.collectAsState(initial = null)
    var heightText by rememberSaveable { mutableStateOf("") }
    var weightText by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    // 保存済みの身長を、入力欄に入れておく(入力し始めていれば、そのまま)
    LaunchedEffect(height) {
        if (heightText.isEmpty()) height?.let { heightText = fmt(it) }
    }
    val latest = entries.firstOrNull()
    val bmi = if (latest != null && height != null) Body.bmi(latest.weightKg, height!!) else null

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("身体", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("いまの体重・BMI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (latest == null) {
                    Text("体重を記録すると、ここに表示します。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile("体重", "${fmt(latest.weightKg)} kg", Modifier.weight(1f), sub = Format.dateShort(latest.timeMs).substringBefore('('))
                        StatTile("BMI", bmi?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-", Modifier.weight(1f), sub = bmi?.let(Body::bmiCategory) ?: "身長を入力してください")
                        StatTile(
                            "標準体重", height?.let(Body::idealWeightKg)?.let { "${fmt(it)} kg" } ?: "-", Modifier.weight(1f),
                            sub = height?.let(Body::idealWeightKg)?.let { diffText(latest.weightKg - it) },
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("身長", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = heightText, onValueChange = { heightText = it.take(6) }, label = { Text("身長(cm)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        val v = heightText.toDoubleOrNull()
                        if (v == null || v !in Body.HEIGHT_RANGE) {
                            message = "身長は ${Body.HEIGHT_RANGE.start.toInt()}〜${Body.HEIGHT_RANGE.endInclusive.toInt()} cm の範囲で入力してください"
                        } else {
                            scope.launch { container.settings.setHeight(v) }
                            message = "身長を保存しました"
                        }
                    }) { Text("保存") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("体重を記録", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = weightText, onValueChange = { weightText = it.take(6) }, label = { Text("体重(kg)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        val v = weightText.toDoubleOrNull()
                        if (v == null || v !in Body.WEIGHT_RANGE) {
                            message = "体重は ${Body.WEIGHT_RANGE.start.toInt()}〜${Body.WEIGHT_RANGE.endInclusive.toInt()} kg の範囲で入力してください"
                        } else {
                            scope.launch {
                                container.body.add(WeightEntry(System.currentTimeMillis(), v))
                                weightText = ""
                                message = "体重を記録しました"
                            }
                        }
                    }) { Text("記録する") }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        if (entries.size >= 2) {
            val sorted = remember(entries) { entries.sortedBy { it.timeMs } }
            val t0 = sorted.first().timeMs
            val xs = remember(sorted) { DoubleArray(sorted.size) { (sorted[it].timeMs - t0) / 86_400_000.0 } } // 日
            val ys = remember(sorted) { DoubleArray(sorted.size) { sorted[it].weightKg } }
            Column {
                SectionHeader("体重の推移")
                LineChart(xs, ys, color = AccentOrange, yLabel = { fmt(it) }, xLabel = { "${it.toInt()}日" }, minSpanY = 2.0)
                Text("${Format.dateShort(t0).substringBefore('(')} から ${xs.last().toInt()}日間", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (entries.isNotEmpty()) {
            SectionHeader("記録")
            Card(Modifier.fillMaxWidth()) {
                Column {
                    entries.take(60).forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(Format.dateTime(e.timeMs), style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${fmt(e.weightKg)} kg", fontWeight = FontWeight.Bold)
                                IconButton(onClick = { scope.launch { container.body.remove(e.timeMs) } }) { Icon(Icons.Filled.Delete, contentDescription = "この記録を削除") }
                            }
                        }
                    }
                }
            }
        }
        Text(
            "身長・体重は、この端末の中にだけ保存します(外部への送信やバックアップはしません)。ランの消費カロリーは、体重・走る速さ・時間から推定した目安で、体重は記録した中でそのランの時点にいちばん近いものを使います。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
    }
}

private fun fmt(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

private fun diffText(diff: Double): String = when {
    kotlin.math.abs(diff) < 0.05 -> "ちょうど標準"
    diff > 0 -> "標準より +${fmt(diff)} kg"
    else -> "標準より ${fmt(diff)} kg"
}
