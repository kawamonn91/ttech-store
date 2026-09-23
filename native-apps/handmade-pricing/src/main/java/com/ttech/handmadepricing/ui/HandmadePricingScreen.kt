package com.ttech.handmadepricing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ttech.handmadepricing.domain.Material
import com.ttech.handmadepricing.domain.PricingInput
import com.ttech.handmadepricing.domain.calcPricing
import com.ttech.handmadepricing.domain.isValidMaterial
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandmadePricingScreen() {
    var materials by remember { mutableStateOf(listOf<Material>()) }
    var materialName by remember { mutableStateOf("") }
    var materialCostText by remember { mutableStateOf("") }
    var workMinutesText by remember { mutableStateOf("60") }
    var hourlyWageText by remember { mutableStateOf("1500") }
    var marginPercentText by remember { mutableStateOf("30") }
    var feePercentText by remember { mutableStateOf("10") }

    val yenFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    fun addMaterial() {
        val cost = materialCostText.toIntOrNull() ?: 0
        if (!isValidMaterial(materialName, cost)) return
        materials = materials + Material(id = UUID.randomUUID().toString(), name = materialName.trim(), cost = cost)
        materialName = ""; materialCostText = ""
    }

    fun removeMaterial(id: String) {
        materials = materials.filter { it.id != id }
    }

    val result = calcPricing(
        PricingInput(
            materials = materials,
            workMinutes = workMinutesText.toIntOrNull() ?: 0,
            hourlyWage = hourlyWageText.toIntOrNull() ?: 0,
            marginPercent = marginPercentText.toIntOrNull() ?: 0,
            feePercent = feePercentText.toIntOrNull() ?: 0,
        ),
    )

    Scaffold(topBar = { TopAppBar(title = { Text("ハンドメイド原価計算機") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("材料費", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = materialName,
                                onValueChange = { materialName = it },
                                label = { Text("材料名") },
                                singleLine = true,
                                modifier = Modifier.weight(2f),
                            )
                            OutlinedTextField(
                                value = materialCostText,
                                onValueChange = { v -> materialCostText = v.filter { it.isDigit() } },
                                label = { Text("円") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedButton(onClick = ::addMaterial, modifier = Modifier.fillMaxWidth()) {
                            Text("材料を追加")
                        }
                        materials.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(m.name)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${yenFormat.format(m.cost)}円")
                                    TextButton(onClick = { removeMaterial(m.id) }) { Text("削除") }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "制作時間・人件費・利益率",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledNumberField("制作時間(分)", workMinutesText, { workMinutesText = it }, Modifier.weight(1f))
                            LabeledNumberField("時給換算(円)", hourlyWageText, { hourlyWageText = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledNumberField("上乗せ利益率(%)", marginPercentText, { marginPercentText = it }, Modifier.weight(1f))
                            LabeledNumberField("販売手数料(%)", feePercentText, { feePercentText = it }, Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("材料費合計", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${yenFormat.format(result.materialTotal)}円")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("人件費", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${yenFormat.format(result.laborCost)}円")
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("原価", style = MaterialTheme.typography.labelLarge)
                            Text("${yenFormat.format(result.baseCost)}円", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("販売手数料込みの推奨価格", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${yenFormat.format(result.suggestedPrice)}円",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "想定利益(手数料差引後): 約${yenFormat.format(result.profit)}円",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledNumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}
