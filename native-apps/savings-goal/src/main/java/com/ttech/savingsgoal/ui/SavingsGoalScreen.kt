package com.ttech.savingsgoal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.savingsgoal.domain.SavingsGoal

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalScreen() {
    var goal by remember { mutableStateOf("3000000") }
    var initial by remember { mutableStateOf("100000") }
    var monthly by remember { mutableStateOf("30000") }
    var annualRate by remember { mutableStateOf("0") }

    val months = SavingsGoal.monthsToReachGoal(
        goal = goal.toDoubleOrNull() ?: 0.0,
        initial = initial.toDoubleOrNull() ?: 0.0,
        monthly = monthly.toDoubleOrNull() ?: 0.0,
        annualRatePercent = annualRate.toDoubleOrNull() ?: 0.0,
    )

    Scaffold(topBar = { TopAppBar(title = { Text("積立目標シミュレーター") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("目標金額(円)", goal) { goal = it }
                        NumberField("現在の貯蓄額(円)", initial) { initial = it }
                        NumberField("毎月の積立額(円)", monthly) { monthly = it }
                        NumberField("想定年利(%、任意)", annualRate, decimal = true) { annualRate = it }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("目標達成までの期間", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (months) {
                            null -> Text("50年以内に到達しません", style = MaterialTheme.typography.titleLarge)
                            0 -> Text("すでに達成しています", style = MaterialTheme.typography.headlineSmall)
                            else -> {
                                Text(SavingsGoal.formatDuration(months), style = MaterialTheme.typography.headlineSmall)
                                Text("(${months}ヶ月)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
