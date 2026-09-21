package com.ttech.cookingunits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ttech.cookingunits.domain.CookingUnits
import com.ttech.cookingunits.domain.IngredientDensityGPerMl
import com.ttech.cookingunits.domain.VolumeUnitMl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingUnitsScreen() {
    var ingredient by remember { mutableStateOf(IngredientDensityGPerMl.keys.first()) }
    var unit by remember { mutableStateOf(VolumeUnitMl.keys.first { it.startsWith("カップ") }) }
    var amountText by remember { mutableStateOf("1") }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val ml = CookingUnits.toMl(amount, unit)
    val grams = CookingUnits.toGrams(ml, ingredient)

    Scaffold(topBar = { TopAppBar(title = { Text("料理の単位換算") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledDropdown(
                            label = "材料",
                            options = IngredientDensityGPerMl.keys.toList(),
                            selected = ingredient,
                            onSelected = { ingredient = it },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { v -> if (v.length <= 8) amountText = v.filter { it.isDigit() || it == '.' } },
                                label = { Text("量") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledDropdown(
                                label = "単位",
                                options = VolumeUnitMl.keys.toList(),
                                selected = unit,
                                onSelected = { unit = it },
                                modifier = Modifier.weight(1.3f),
                            )
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("体積", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${formatNumber(ml)}ml", style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("重さ($ingredient)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${formatNumber(grams)}g", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }

            item {
                Text(
                    "※ 重さへの換算は一般的な密度の目安値です。メーカーや状態により多少前後します。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}
