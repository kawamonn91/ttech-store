package com.ttech.navi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ttech.navi.BuildConfig
import com.ttech.navi.NaviContainer
import com.ttech.navi.data.CurrentLocation
import com.ttech.navi.domain.NaviException
import com.ttech.navi.domain.NaviSettings
import com.ttech.navi.domain.Phrases
import com.ttech.navi.domain.Place
import com.ttech.navi.domain.Route
import com.ttech.navi.domain.RouteChooser
import com.ttech.navi.domain.TollEstimate
import com.ttech.navi.domain.VehicleClass
import com.ttech.track.domain.MapStyle
import com.ttech.track.domain.RouteSegments
import com.ttech.track.ui.RouteMapView

/**
 * 目的地を選んだあとの確認画面。ルートの全体・距離・所要時間・到着予定と、ナビの開始時に読み上げる天気の案内を見せる。
 * [onStart] には、天気の案内(取れなかったときは null。サービスが作り直す)も渡す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    container: NaviContainer,
    place: Place,
    onBack: () -> Unit,
    onStart: (route: Route, briefing: List<String>?, simulateMps: Double?) -> Unit,
) {
    val context = LocalContext.current
    val perm by rememberPermState()
    val actions = rememberPermissionActions()
    val settings by container.settings.settings.collectAsState(initial = NaviSettings())
    var retry by remember { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf<List<Route>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var briefing by remember { mutableStateOf<List<String>?>(null) }
    var briefingError by remember { mutableStateOf<String?>(null) }
    var departMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // 経路の候補を取る(公開サーバーは「高速道路を使わない」の指定を受け付けないので、
    // 代わりに候補[alternatives]をいくつか取り、有料道路の有無・進入方向を選べるようにする)
    LaunchedEffect(place, retry, perm.canNavigate) {
        candidates = emptyList()
        selectedIndex = 0
        error = null
        briefing = null
        briefingError = null
        if (!perm.canNavigate) return@LaunchedEffect
        try {
            val from = CurrentLocation.get(context) ?: throw NaviException("現在地を取得できませんでした。GPSを受信できる場所で、もう一度お試しください")
            departMs = System.currentTimeMillis()
            val list = container.osrm.routes(from, place.latLon)
            candidates = distinctCandidates(list)
            selectedIndex = candidates.indexOf(RouteChooser.pickDefault(candidates)).coerceAtLeast(0)
            container.recents.add(place)
        } catch (e: NaviException) {
            error = e.message
        }
    }

    val route = candidates.getOrNull(selectedIndex)

    // 天気の案内は、選んだ経路(所要時間・通る場所)が変わるたびに作り直す
    LaunchedEffect(route, departMs) {
        val r = route ?: return@LaunchedEffect
        briefing = null
        briefingError = null
        try {
            briefing = container.briefing.briefing(r, departMs)
        } catch (e: NaviException) {
            briefingError = e.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(place.name, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val r = route
            Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFFEEEDE9))) {
                if (r != null) {
                    RouteMapView(RouteSegments(listOf(r.line.points), emptyList()), container.tiles, MapStyle.Light, NaviRouteStyle)
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
                    }
                    if (candidates.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("経路を選ぶ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            candidates.forEachIndexed { i, c ->
                                RouteOptionCard(c, i, selectedIndex == i, settings.vehicleClass) { selectedIndex = i }
                            }
                        }
                    } else if (r.tollDistanceM >= 500.0) {
                        Text(
                            "高速道路を使うルートです(通行料金の目安 約${TollEstimate.estimate(r.tollDistanceM, settings.vehicleClass)}円)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("天気の案内(ナビの開始時に読み上げます)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val lines = briefing
                            when {
                                lines != null -> lines.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
                                briefingError != null -> Text("天気予報を取得できませんでした(${briefingError})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("天気を調べています…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    Text("天気データ: Open-Meteo.com", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "ルートは、公開の経路検索サービス(OSRM)による目安です。実際の交通規制・渋滞は反映されません。標識と道路の状況を優先して運転してください。" +
                            "通行料金は、車種ごとのおおまかな目安(1kmあたりの単価から計算)で、実際の料金とは異なります。正式な料金はNEXCO等の公式情報でご確認ください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val r = route
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { r?.let { onStart(it, briefing, null) } }, enabled = r != null && perm.canNavigate, modifier = Modifier.fillMaxWidth()) {
                Text("ナビを開始")
            }
            if (BuildConfig.DEBUG) {
                OutlinedButton(onClick = { r?.let { onStart(it, briefing, 16.7) } }, enabled = r != null, modifier = Modifier.fillMaxWidth()) {
                    Text("デモ走行(開発用・時速60km・10倍速)")
                }
            }
        }
    }
}

/** 見た目上、区別する意味のある候補だけを残す(OSRMがほぼ同じ経路を複数返すことがあるため) */
private fun distinctCandidates(routes: List<Route>): List<Route> {
    val seen = HashSet<Triple<Long, Boolean, String?>>()
    val out = ArrayList<Route>()
    for (r in routes) {
        val key = Triple((r.distanceM / 200).toLong(), r.tollDistanceM >= 500.0, r.maneuvers.lastOrNull()?.modifier)
        if (seen.add(key)) out.add(r)
    }
    return out
}

/** 経路の候補1件分。高速道路の有無(と通行料金の目安)・目的地への進入方向を添えて、タップで選べる */
@Composable
private fun RouteOptionCard(route: Route, index: Int, selected: Boolean, vehicleClass: VehicleClass, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "経路${index + 1}・${Phrases.distanceExact(route.distanceM)}・${Phrases.duration(route.durationS)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (route.tollDistanceM >= 500.0) {
                    "・高速道路を使います(通行料金の目安 約${TollEstimate.estimate(route.tollDistanceM, vehicleClass)}円)"
                } else {
                    "・高速道路を使いません"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (route.maneuvers.lastOrNull()?.modifier != "right") {
                Text("・目的地に左折で入れます", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
