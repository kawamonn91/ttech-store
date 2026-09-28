package com.ttech.navi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.navi.NaviContainer
import com.ttech.navi.domain.NaviSettings
import com.ttech.navi.domain.Phrases
import com.ttech.navi.nav.NavService
import com.ttech.navi.nav.NavState
import com.ttech.navi.nav.NavView
import java.time.Instant
import kotlinx.coroutines.launch

private val BannerBlue = Color(0xFF1D4ED8)

/** 案内中の画面: 全面の地図の上に、次の曲がり角(と、その次)、残りの距離・時間・到着予定、操作を重ねる */
@Composable
fun NavigationScreen(container: NaviContainer, view: NavView) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = NaviSettings())
    val notice by NavState.notice.collectAsState()
    val voiceAvailable by NavState.voiceAvailable.collectAsState()
    var confirmEnd by remember { mutableStateOf(false) }

    // 案内中は、画面を消さない
    val host = LocalView.current
    DisposableEffect(Unit) {
        host.keepScreenOn = true
        onDispose { host.keepScreenOn = false }
    }
    BackHandler { if (view.arrival != null) NavService.stop(context) else confirmEnd = true }

    Box(Modifier.fillMaxSize()) {
        NavMap(view, container.tiles, settings.headingUp, settings.mapDark, Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ManeuverBanner(view)
            when {
                view.gpsWaiting -> StatusChip("GPSを待っています…")
                view.rerouting -> StatusChip("ルートを再検索しています…")
                view.offRoute -> StatusChip("ルートから外れています")
            }
            notice?.let { StatusChip(it, error = true) }
            if (voiceAvailable == false && settings.voice) {
                StatusChip("音声(日本語)が使えません。端末の設定の「テキスト読み上げ」で、日本語の音声データを入れてください", error = true)
            }
        }

        TripPanel(
            view = view,
            voiceOn = settings.voice,
            onToggleVoice = { scope.launch { container.settings.update { it.copy(voice = !it.voice) } } },
            onEnd = { if (view.arrival != null) NavService.stop(context) else confirmEnd = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("案内を終了しますか?") },
            confirmButton = {
                Button(
                    onClick = { confirmEnd = false; NavService.stop(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("終了する") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("続ける") } },
        )
    }

    view.arrival?.let { arrival ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("目的地に到着しました") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(view.destination.name, style = MaterialTheme.typography.titleMedium)
                    LabeledRow("走行距離", Phrases.distanceExact(arrival.distanceM))
                    LabeledRow("所要時間", Phrases.duration(arrival.durationMs / 1000.0))
                    Text("おつかれさまでした。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { NavService.stop(context) }) { Text("終了") } },
        )
    }
}

/** 次の曲がり角(大きく)と、その次の曲がり角(その下に小さく)。ご要望の「もう一つ先の曲がる方向」 */
@Composable
private fun ManeuverBanner(view: NavView) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BannerBlue, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(6.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(maneuverIcon(view.next), contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(view.distToNextM?.let(::shortDistance) ?: "", fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(view.next?.let(Phrases::maneuver) ?: "案内中", fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val road = view.next?.roadName.orEmpty()
                    if (road.isNotEmpty()) Text("→ $road", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val after = view.afterNext
            val gap = view.gapM
            if (after != null && gap != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(maneuverIcon(after), contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("その後 ${shortDistance(gap)} 先で ${Phrases.maneuver(after)}", fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, error: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** 残りの距離・時間・到着予定と、音声のオン・オフ、案内の終了 */
@Composable
private fun TripPanel(view: NavView, voiceOn: Boolean, onToggleVoice: () -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("残り", shortDistance(view.remainingM))
                StatTile("時間", Phrases.duration(view.remainingS))
                StatTile("到着", view.etaMs?.let { clockText(it) } ?: "-")
                StatTile("速度", "${(view.speedKmh ?: 0.0).toInt()} km/h")
            }
            view.regionText?.let { Text("現在地: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            view.lastSpoken?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onToggleVoice) {
                    Icon(
                        if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (voiceOn) "音声をオフにする" else "音声をオンにする",
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(view.destination.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = onEnd) { Text(if (view.arrival != null) "閉じる" else "案内を終了") }
            }
        }
    }
}

private fun clockText(epochMs: Long): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(Phrases.JST)
    return "%d:%02d".format(t.hour, t.minute)
}

