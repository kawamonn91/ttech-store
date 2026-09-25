package com.ttech.runtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.domain.RunSettings
import com.ttech.track.domain.Format
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: RunContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = RunSettings())
    val perm by rememberPermState()
    val actions = rememberPermissionActions { }
    val runs by container.repository.runs.collectAsState()
    var confirmDeleteAll by remember { mutableStateOf(false) }

    fun update(transform: (RunSettings) -> RunSettings) = scope.launch { container.settings.update(transform) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("設定") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("計測中は画面を消さない", style = MaterialTheme.typography.titleMedium)
                            Text("「計測」の画面を見ている間だけ、画面が暗くならないようにします", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.keepScreenOn, onCheckedChange = { on -> update { it.copy(keepScreenOn = on) } })
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("これより短いランは記録しない", style = MaterialTheme.typography.titleMedium)
                    Text("誤ってスタートしたときなど、ごく短い記録を残さないための設定です", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RunSettings.MIN_DISTANCE_CHOICES.forEach { m ->
                            FilterChip(selected = settings.minDistanceM == m, onClick = { update { it.copy(minDistanceM = m) } }, label = { Text(if (m == 0) "なし" else Format.distance(m.toDouble())) })
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("地図の見た目", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = settings.mapStyleDark, onClick = { update { it.copy(mapStyleDark = true) } }, label = { Text("ダーク") })
                        FilterChip(selected = !settings.mapStyleDark, onClick = { update { it.copy(mapStyleDark = false) } }, label = { Text("ライト") })
                    }
                    Text("すでにある記録の画像は、詳細画面の「地図の画像を作り直す」で変わります", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("権限の状況", style = MaterialTheme.typography.titleMedium)
                    LabeledRow("位置情報", if (perm.location) "許可済み" else "未許可")
                    LabeledRow("通知", if (perm.notifications) "許可済み" else "未許可")
                    LabeledRow("端末のGPS", if (perm.gpsOn) "オン" else "オフ")
                    if (!perm.location) OutlinedButton(onClick = actions.requestLocation) { Text("位置情報を許可する") }
                    else if (!perm.notifications) OutlinedButton(onClick = actions.openAppSettings) { Text("通知を許可する(設定を開く)") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("データ・プライバシー", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "ランの記録(位置・速度など)と、身長・体重は、この端末の中だけに保存します。サーバーへの送信や、クラウドへのバックアップはしません。" +
                            "地図の背景を表示するときだけ、表示する範囲の地図画像を OpenStreetMap から取得します(記録の内容は送りません)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("保存している記録: ${runs.size}件", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { confirmDeleteAll = true }, enabled = runs.isNotEmpty()) { Text("すべての記録を削除") }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

        StatusBarScrim(Modifier.align(Alignment.TopStart))
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "すべての記録を削除",
            message = "${runs.size}件の記録(ルート・走行データ・地図の画像)がすべて削除されます。体重の記録は消えません。元には戻せません。",
            confirmLabel = "すべて削除する",
            danger = true,
            onConfirm = { scope.launch { container.repository.deleteAll() } },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}
