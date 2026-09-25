package com.ttech.runtracker.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.RunState
import com.ttech.runtracker.domain.LiveRun
import com.ttech.runtracker.domain.RunSettings
import com.ttech.runtracker.recording.RunService
import com.ttech.track.domain.Format
import kotlinx.coroutines.delay

/** 計測の画面。走っていないときは「スタート」、走っているときは時間・距離・ペースを大きく出す */
@Composable
fun RecordScreen(container: RunContainer, onOpenBody: () -> Unit) {
    val live by RunState.live.collectAsState()
    val settings by container.settings.settings.collectAsState(initial = RunSettings())
    val current = live

    // 計測中にこの画面を見ている間は、画面が消えないようにする
    val view = LocalView.current
    DisposableEffect(current != null, settings.keepScreenOn) {
        val activity = view.context as? android.app.Activity
        if (current != null && settings.keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("計測", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        if (current == null) IdlePanel(container, onOpenBody) else LivePanel(current)
    }
}

@Composable
private fun IdlePanel(container: RunContainer, onOpenBody: () -> Unit) {
    val context = LocalContext.current
    val perm by rememberPermState()
    val actions = rememberPermissionActions { }
    val notice by RunState.notice.collectAsState()
    val weights by container.body.entries.collectAsState()

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 大きな「スタート」ボタン
        val enabled = perm.canRecord
        Box(
            Modifier.size(190.dp).clip(CircleShape)
                .background(if (enabled) AccentOrange else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled) { RunService.start(context) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                Text("スタート", color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 足りない権限・設定を、ひとつずつ案内する
        if (!perm.location) {
            Warning("位置情報の許可が必要です。走ったルートを記録するために使います", "許可する", actions.requestLocation)
        } else if (!perm.gpsOn) {
            Warning("端末の位置情報(GPS)がオフです", "設定を開く", actions.openLocationSettings)
        } else if (!perm.notifications) {
            Warning("通知を許可すると、走っている間の距離・ペースを通知でも確認でき、通知から一時停止・終了できます", "許可する", actions.requestLocation)
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }

        Text(
            "スタートしたら、画面を閉じてポケットに入れても記録を続けます。開けた場所で、GPSがつかまるのを待ってから走り始めるとより正確です。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (weights.isEmpty()) {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenBody)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("体重を入力しておきましょう", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "消費カロリーを、体重・ペース・時間から推定します。入力していない間は、標準の体重(60kg)で計算します。タップして「身体」を開く",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Warning(text: String, action: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, color = WarnAmber, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onClick) { Text(action) }
    }
}

/** 計測中の表示。時間を大きく、距離・現在のペース・平均ペースを並べる */
@Composable
private fun LivePanel(live: LiveRun) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val gps by RunState.gps.collectAsState()
    var confirmStop by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Pill(if (live.paused) "⏸ 一時停止中" else "● 計測中", if (live.paused) WarnAmber else AccentOrange)
            Column {
                Text("時間", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.clock(live.activeMs(now)), fontSize = 64.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("距離", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Format.distanceKmNumber(live.distanceM), fontSize = 48.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        Text("km", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("現在のペース", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (live.paused) "-" else live.paceSecPerKm?.let(Format::paceNumber) ?: "-",
                        fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    )
                    Text("/km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("平均ペース", live.avgPaceSecPerKm(now)?.let(Format::pace) ?: "-")
                StatTile("経過", Format.clock(live.elapsedMs(now)))
            }
            Text(
                buildString {
                    append("GPS: ")
                    append(if (live.points == 0) "測位を待っています" else "精度 ${live.accuracyM?.let { "${it.toInt()}m" } ?: "-"}")
                    if (gps.satellitesVisible > 0) append(" ・ 衛星 ${gps.satellitesUsed}/${gps.satellitesVisible}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (live.paused) RunService.resume(context) else RunService.pause(context) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(if (live.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                    Text(if (live.paused) "再開" else "一時停止", modifier = Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = { confirmStop = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("終了", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
    if (confirmStop) {
        ConfirmDialog(
            title = "ランを終了しますか?",
            message = "ここまでの記録を保存します。",
            confirmLabel = "終了して保存",
            onConfirm = { RunService.stop(context) },
            onDismiss = { confirmStop = false },
        )
    }
}
