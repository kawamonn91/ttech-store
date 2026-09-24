package com.ttech.plantwatering.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.plantwatering.data.PlantStore
import com.ttech.plantwatering.domain.DEFAULT_INTERVAL_DAYS
import com.ttech.plantwatering.domain.Plant
import com.ttech.plantwatering.domain.isDue
import com.ttech.plantwatering.domain.isValidPlantName
import com.ttech.plantwatering.domain.normalizeIntervalDays
import com.ttech.plantwatering.domain.remainingDays
import com.ttech.plantwatering.domain.sortedByUrgency
import com.ttech.plantwatering.reminder.scheduleDailyCheck
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantWateringScreen() {
    val context = LocalContext.current
    val store = remember { PlantStore(context) }
    val scope = rememberCoroutineScope()
    val plants by store.plants.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var intervalText by remember { mutableStateOf(DEFAULT_INTERVAL_DAYS.toString()) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        scheduleDailyCheck(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun addPlant() {
        if (!isValidPlantName(name)) return
        // launch の中で name を読むと、直後のクリアより後に評価されて空になり得るため、先に値を確定させる。
        val plant = Plant(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            intervalDays = normalizeIntervalDays(intervalText.toIntOrNull()),
            lastWateredDate = LocalDate.now().toString(),
        )
        scope.launch { store.add(plant) }
        name = ""
    }

    val today = LocalDate.now()
    val sorted = plants.sortedByUrgency(today)

    Scaffold(topBar = { TopAppBar(title = { Text("観葉植物の水やりリマインダー") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("植物の名前") },
                            placeholder = { Text("例: モンステラ") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { v -> intervalText = v.filter { it.isDigit() }.take(3) },
                            label = { Text("水やりの間隔(日)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::addPlant, enabled = isValidPlantName(name), modifier = Modifier.fillMaxWidth()) {
                            Text("登録する")
                        }
                    }
                }
            }

            if (sorted.isEmpty()) {
                item {
                    Text(
                        "まだ植物が登録されていません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }

            items(sorted, key = Plant::id) { plant ->
                val remaining = remainingDays(plant, today)
                val due = isDue(plant, today)
                Card(
                    Modifier.fillMaxWidth(),
                    border = if (due) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(plant.name, style = MaterialTheme.typography.titleMedium)
                        val status = if (due) "水やりの時期です(${-remaining}日超過)" else "あと${remaining}日"
                        Text(
                            "$status ・ 最終: ${plant.lastWateredDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (due) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (due) FontWeight.Medium else null,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = { scope.launch { store.waterNow(plant.id, LocalDate.now()) } }) {
                                Text("水やり完了")
                            }
                            TextButton(onClick = { scope.launch { store.remove(plant.id) } }) { Text("削除") }
                        }
                    }
                }
            }
        }
    }
}
