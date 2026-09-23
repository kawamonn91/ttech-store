package com.ttech.furusatonozei.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.ttech.furusatonozei.domain.CalcInput
import com.ttech.furusatonozei.domain.calcDonationLimit
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FurusatoNozeiScreen() {
    var salaryIncomeText by remember { mutableStateOf("6000000") }
    var hasSpouse by remember { mutableStateOf(false) }
    var dependentsText by remember { mutableStateOf("0") }

    val limit = calcDonationLimit(
        CalcInput(
            salaryIncome = salaryIncomeText.toIntOrNull() ?: 0,
            hasSpouse = hasSpouse,
            dependents = dependentsText.toIntOrNull() ?: 0,
        ),
    )
    val yenFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    Scaffold(topBar = { TopAppBar(title = { Text("ふるさと納税限度額計算機") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                "給与収入(額面・年収、円)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = salaryIncomeText,
                                onValueChange = { v -> salaryIncomeText = v.filter { it.isDigit() } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hasSpouse, onCheckedChange = { hasSpouse = it })
                            Text("配偶者控除の対象となる配偶者がいる(配偶者の年収103万円以下)")
                        }
                        Column {
                            Text(
                                "扶養家族の人数(16歳以上、控除対象扶養親族)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = dependentsText,
                                onValueChange = { v -> dependentsText = v.filter { it.isDigit() } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ふるさと納税 寄付限度額の目安", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${yenFormat.format(limit)}円",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            item {
                Text(
                    "※ 本結果は給与収入のみを前提とした概算(目安)です。医療費控除・住宅ローン控除・iDeCo等の他の控除や、" +
                        "特定扶養親族(19〜22歳、控除額が異なります)、給与以外の所得がある場合は結果が変動します。" +
                        "正確な金額は自治体の窓口や税理士、お使いのふるさと納税サイトのシミュレーターでご確認ください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
