package com.ttech.runtracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ttech.runtracker.RunContainer
import com.ttech.runtracker.RunState
import com.ttech.track.domain.Format
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    Feed("記録", Icons.Filled.Home),
    Record("計測", Icons.AutoMirrored.Filled.DirectionsRun),
    Progress("成績", Icons.Filled.BarChart),
    Body("身体", Icons.Filled.MonitorWeight),
}

/** 画面の切り替え。下のタブ(記録・計測・成績・身体)と、記録の詳細・設定。戻るボタンで前の画面に戻る */
@Composable
fun RunApp(container: RunContainer, openRunId: MutableState<String?>) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val live by RunState.live.collectAsState()
    val finished by RunState.finishedId.collectAsState()

    LaunchedEffect(Unit) {
        container.repository.refresh()
        container.recoverUnfinished()
    }
    // 通知から開かれたときは、その記録の詳細を出す
    LaunchedEffect(openRunId.value) {
        openRunId.value?.let {
            detailId = it
            showSettings = false
            openRunId.value = null
        }
    }
    // 計測を終えたら、その記録の詳細を出す
    LaunchedEffect(finished) {
        finished?.let {
            detailId = it
            showSettings = false
            tabIndex = Tab.Feed.ordinal
            RunState.finishedId.value = null
        }
    }
    BackHandler(enabled = detailId != null || showSettings || tabIndex != 0) {
        when {
            detailId != null -> detailId = null
            showSettings -> showSettings = false
            else -> tabIndex = 0
        }
    }

    val id = detailId
    when {
        id != null -> DetailScreen(container, id, onBack = { detailId = null }, onDeleted = { detailId = null })
        showSettings -> SettingsScreen(container, onBack = { showSettings = false })
        else -> Scaffold(
            bottomBar = {
                Column {
                    // 計測中は、別のタブを見ていても、状況を出しておく
                    if (live != null && tabIndex != Tab.Record.ordinal) MiniLive(onClick = { tabIndex = Tab.Record.ordinal })
                    NavigationBar {
                        Tab.entries.forEachIndexed { i, tab ->
                            NavigationBarItem(
                                selected = tabIndex == i,
                                onClick = { tabIndex = i },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = AccentOrange, selectedTextColor = AccentOrange),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (Tab.entries[tabIndex]) {
                    Tab.Feed -> FeedScreen(container, onOpen = { detailId = it }, onSettings = { showSettings = true })
                    Tab.Record -> RecordScreen(container, onOpenBody = { tabIndex = Tab.Body.ordinal })
                    Tab.Progress -> ProgressScreen(container, onOpen = { detailId = it })
                    Tab.Body -> BodyScreen(container)
                }
            }
        }
    }
}

/** 計測中の状況を、下のタブの上に1行で出す */
@Composable
private fun MiniLive(onClick: () -> Unit) {
    val live by RunState.live.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val l = live ?: return
    Surface(color = AccentOrange, contentColor = androidx.compose.ui.graphics.Color.White, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (l.paused) "⏸ 一時停止中" else "● 計測中", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text("${Format.distance(l.distanceM)}   ${Format.clock(l.activeMs(now))}", fontWeight = FontWeight.Bold)
            Text("開く", style = MaterialTheme.typography.labelLarge)
        }
    }
}
