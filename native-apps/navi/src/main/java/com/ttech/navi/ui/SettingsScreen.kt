package com.ttech.navi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.navi.NaviContainer
import com.ttech.navi.domain.NaviSettings
import com.ttech.navi.nav.Speaker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: NaviContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = NaviSettings())
    fun update(transform: (NaviSettings) -> NaviSettings) = scope.launch { container.settings.update(transform) }

    // 「音声のテスト」用。画面を離れるときに片づける
    val testSpeaker = remember { Speaker(context) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { testSpeaker.shutdown() } }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("設定") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SwitchCard("音声で案内する", "曲がり角・県や市に入ったとき・天気・到着を、声でお知らせします", settings.voice) { on -> update { it.copy(voice = on) } }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("読み上げの速さ", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NaviSettings.SPEECH_RATES.forEach { rate ->
                            FilterChip(
                                selected = settings.speechRate == rate,
                                onClick = { update { it.copy(speechRate = rate) } },
                                label = { Text(rateLabel(rate)) },
                            )
                        }
                    }
                    OutlinedButton(onClick = {
                        testSpeaker.rate = settings.speechRate
                        testSpeaker.speak("200メートル先、右折です。その後、300メートル先、左折です。")
                    }) { Text("音声のテスト") }
                }
            }

            SwitchCard("出発時に天気を案内する", "到着予定時刻と、目的地・道中の天気の予報を、ナビの開始時に読み上げます", settings.weatherBriefing) { on -> update { it.copy(weatherBriefing = on) } }
            SwitchCard("県・市区町村に入ったら案内する", "「宮城県、白石市に入りました」のように、境界をまたいだときにお知らせします", settings.regionAnnouncements) { on -> update { it.copy(regionAnnouncements = on) } }
            SwitchCard("進行方向を上にして地図を回す", "オフにすると、常に北が上になります", settings.headingUp) { on -> update { it.copy(headingUp = on) } }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("地図の見た目", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !settings.mapDark, onClick = { update { it.copy(mapDark = false) } }, label = { Text("ライト") })
                        FilterChip(selected = settings.mapDark, onClick = { update { it.copy(mapDark = true) } }, label = { Text("ダーク") })
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("データ・プライバシー", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "アカウント登録・広告・利用状況の解析はありません。目的地の履歴は、この端末の中だけに保存します。" +
                            "案内のために、次の公開サービスと通信します(自分たちのサーバーは持ちません)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        "ルート検索: OSRM の公開サーバー(出発地と目的地の座標)",
                        "目的地の検索: OpenStreetMap Nominatim・国土地理院(入力した文字と、現在地の周辺)",
                        "天気: Open-Meteo(ルート沿いの地点の座標)",
                        "現在の市区町村: 国土地理院(案内中、約10秒ごとに現在地の座標)",
                        "地図: OpenStreetMap のタイル(表示する範囲の地図画像)",
                    ).forEach { Text("・$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedButton(onClick = { scope.launch { container.recents.clear() } }) { Text("最近の目的地を消す") }
                }
            }
            Spacer(Modifier.height(48.dp)) // ナビゲーションバーに最後のカードが隠れないように
        }
    }
}

private fun rateLabel(rate: Float) = when (rate) {
    0.8f -> "ゆっくり"
    1.0f -> "ふつう"
    1.2f -> "少し速い"
    1.4f -> "速い"
    else -> "${rate}倍"
}

@Composable
private fun SwitchCard(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
