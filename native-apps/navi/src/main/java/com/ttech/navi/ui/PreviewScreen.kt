package com.ttech.navi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ttech.navi.domain.Phrases
import com.ttech.navi.domain.Place
import com.ttech.navi.domain.Route
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
    var retry by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var briefing by remember { mutableStateOf<List<String>?>(null) }
    var briefingError by remember { mutableStateOf<String?>(null) }
    var departMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(place, retry, perm.canNavigate) {
        route = null
        error = null
        briefing = null
        briefingError = null
        if (!perm.canNavigate) return@LaunchedEffect
        try {
            val from = CurrentLocation.get(context) ?: throw NaviException("現在地を取得できませんでした。GPSを受信できる場所で、もう一度お試しください")
            departMs = System.currentTimeMillis()
            val r = container.osrm.route(from, place.latLon)
            route = r
            container.recents.add(place)
            try {
                briefing = container.briefing.briefing(r, departMs)
            } catch (e: NaviException) {
                briefingError = e.message
            }
        } catch (e: NaviException) {
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
                        "ルートは、公開の経路検索サービス(OSRM)による目安です。実際の交通規制・渋滞は反映されません。標識と道路の状況を優先して運転してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val r = route
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
