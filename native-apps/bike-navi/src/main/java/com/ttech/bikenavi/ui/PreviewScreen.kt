package com.ttech.bikenavi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.bikenavi.BikeContainer
import com.ttech.bikenavi.BuildConfig
import com.ttech.bikenavi.data.CurrentLocation
import com.ttech.bikenavi.data.SupplyStop
import com.ttech.bikenavi.domain.BikeException
import com.ttech.bikenavi.domain.BikeSettings
import com.ttech.bikenavi.domain.Phrases
import com.ttech.bikenavi.domain.Place
import com.ttech.bikenavi.domain.Route
import com.ttech.bikenavi.domain.RouteStyle
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.RouteSegments
import com.ttech.track.ui.RouteMapView
import kotlinx.coroutines.launch

/** 目的地を選んだあとの確認画面。ルートの全体・標高グラフ・補給スポット・天気の案内を見せる */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreviewScreen(
    container: BikeContainer,
    place: Place,
    onBack: () -> Unit,
    onStart: (route: Route, briefing: List<String>?, simulateMps: Double?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val perm by rememberPermState()
    val actions = rememberPermissionActions()
    val settings by container.settings.settings.collectAsState(initial = BikeSettings())
    var retry by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var weather by remember { mutableStateOf<List<String>?>(null) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var supplyStops by remember { mutableStateOf<List<SupplyStop>?>(null) }
    var departMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(place, retry, perm.canNavigate, settings.routeStyle) {
        route = null
        error = null
        weather = null
        weatherError = null
        supplyStops = null
        if (!perm.canNavigate) return@LaunchedEffect
        try {
            val from = CurrentLocation.get(context) ?: throw BikeException("現在地を取得できませんでした。GPSを受信できる場所で、もう一度お試しください")
            departMs = System.currentTimeMillis()
            val r = container.brouter.route(from, place.latLon, settings.routeStyle)
            route = r
            container.recents.add(place)
            try {
                weather = container.briefing.weatherBriefing(r, departMs)
            } catch (e: BikeException) {
                weatherError = e.message
            }
            supplyStops = runCatching { container.briefing.supplyBriefing(r, departMs, settings.restIntervalHours) }.getOrDefault(emptyList())
        } catch (e: BikeException) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(place.name, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val r = route
            Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFFEEEDE9))) {
                if (r != null) {
                    RouteMapView(RouteSegments(listOf(r.line.points), emptyList()), container.tiles, MapStyle.Light, BikeRouteStyle)
                } else if (error == null && perm.canNavigate) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (place.detail.isNotEmpty()) Text(place.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (!perm.canNavigate) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!perm.location) {
                                Text("ルートを調べるには、位置情報の許可が必要です")
                                Button(onClick = actions.requestLocation) { Text("位置情報を許可する") }
                            } else {
                                Text("端末の位置情報(GPS)がオフです")
                                OutlinedButton(onClick = actions.openLocationSettings) { Text("設定を開く") }
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("走り方", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RouteStyle.entries.forEach { s ->
                                FilterChip(
                                    selected = settings.routeStyle == s,
                                    onClick = { scope.launch { container.settings.update { it.copy(routeStyle = s) } } },
                                    label = { Text(s.label) },
                                )
                            }
                        }
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { retry++ }) { Text("もう一度調べる") }
                }

                if (r != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatTile("距離", Phrases.distanceExact(r.distanceM))
                            StatTile("所要時間", Phrases.duration(r.durationS))
                            StatTile("到着予定", Phrases.clock(departMs + (r.durationS * 1000).toLong()))
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatTile("獲得標高(登り)", "${r.ascendM.toInt()} m", valueSize = 18.sp)
                            StatTile("下り", "${r.descendM.toInt()} m", valueSize = 18.sp)
                        }
                    }

                    val twoStage = r.maneuvers.count { it.twoStageRightTurn }
                    if (twoStage > 0) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("二段階右折の目安", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "このルートには、広い道路への右折が${twoStage}か所あります。二段階右折が必要になることがあります(目安です。実際の標識・交通規制に従ってください)。",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("勾配グラフ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            ElevationChart(r.elevation, Modifier.fillMaxWidth())
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("補給・休憩スポット", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            when {
                                supplyStops == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("探しています…", style = MaterialTheme.typography.bodyMedium)
                                }
                                supplyStops!!.isEmpty() -> Text("近くに、提案できるスポットが見つかりませんでした", style = MaterialTheme.typography.bodyMedium)
                                else -> supplyStops!!.forEach { stop ->
                                    Text("・${stop.place.name}(${stop.kind.label})", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("天気の案内(ナビの開始時に読み上げます)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val lines = weather
                            when {
                                lines != null -> lines.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
                                weatherError != null -> Text("天気予報を取得できませんでした(${weatherError})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("天気を調べています…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    Text("天気データ: Open-Meteo.com ・ 補給スポット: OpenStreetMap(Overpass)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "ルートは、公開の経路検索サービス(BRouter)による目安です。実際の交通規制・道路状況は反映されません。標識と道路の状況を優先して走行してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val r = route
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { r?.let { onStart(it, weather, null) } }, enabled = r != null && perm.canNavigate, modifier = Modifier.fillMaxWidth()) {
                Text("ナビを開始")
            }
            if (BuildConfig.DEBUG) {
                OutlinedButton(onClick = { r?.let { onStart(it, weather, 5.0) } }, enabled = r != null, modifier = Modifier.fillMaxWidth()) {
                    Text("デモ走行(開発用・時速18km・10倍速)")
                }
            }
        }
    }
}
