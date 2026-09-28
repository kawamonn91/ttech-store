package com.ttech.navi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.navi.NaviContainer
import com.ttech.navi.domain.Place
import com.ttech.navi.nav.NavService
import com.ttech.navi.nav.NavState
import com.ttech.navi.nav.PendingStart
import kotlinx.coroutines.delay

/** 画面の切り替え。案内中は案内の画面。そうでなければ 目的地の検索 → ルートの確認 →(ナビを開始)。設定・地図で選ぶ画面も */
@Composable
fun NaviApp(container: NaviContainer, debugPlace: MutableState<Place?>) {
    val context = LocalContext.current
    val view by NavState.view.collectAsState()
    var preview by remember { mutableStateOf<Place?>(null) }
    var picking by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }

    // 開発用: adb から目的地を渡されたら、そのルートの確認画面を開く(デバッグビルドだけ)
    LaunchedEffect(debugPlace.value) {
        debugPlace.value?.let {
            preview = it
            picking = false
            showSettings = false
            debugPlace.value = null
        }
    }
    // サービスが案内を始めたら、案内の画面に切り替わる。始められなかったときは、しばらくして元の画面に戻す
    LaunchedEffect(view) { if (view != null) starting = false }
    LaunchedEffect(starting) {
        if (starting) {
            delay(8_000)
            starting = false
        }
    }
    BackHandler(enabled = view == null && (preview != null || picking || showSettings)) {
        when {
            showSettings -> showSettings = false
            picking -> picking = false
            else -> preview = null
        }
    }

    val current = view
    when {
        current != null -> NavigationScreen(container, current)
        starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("案内を始めています…")
            }
        }
        showSettings -> SettingsScreen(container, onBack = { showSettings = false })
        picking -> MapPickerScreen(container, onPicked = { preview = it; picking = false }, onBack = { picking = false })
        preview != null -> {
            val place = preview!!
            PreviewScreen(
                container, place,
                onBack = { preview = null },
                onStart = { route, briefing, simulate ->
                    NavState.pending = PendingStart(route, place, briefing, simulate)
                    starting = true
                    preview = null
                    NavService.start(context)
                },
            )
        }
        else -> HomeScreen(container, onPick = { preview = it }, onPickOnMap = { picking = true }, onSettings = { showSettings = true })
    }
}
