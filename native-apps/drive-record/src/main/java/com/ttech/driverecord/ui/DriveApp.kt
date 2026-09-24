package com.ttech.driverecord.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ttech.driverecord.DriveContainer

/** 画面の切り替え。一覧 → 詳細 / 設定。戻るボタンで一覧に戻る */
@Composable
fun DriveApp(container: DriveContainer, openDriveId: MutableState<String?>) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.repository.refresh() }
    // 通知から開かれたときは、その記録の詳細を出す
    LaunchedEffect(openDriveId.value) {
        openDriveId.value?.let {
            detailId = it
            showSettings = false
            openDriveId.value = null
        }
    }
    BackHandler(enabled = detailId != null || showSettings) {
        if (detailId != null) detailId = null else showSettings = false
    }

    val id = detailId
    when {
        id != null -> DetailScreen(container, id, onBack = { detailId = null }, onDeleted = { detailId = null })
        showSettings -> SettingsScreen(container, onBack = { showSettings = false })
        else -> HomeScreen(container, onOpen = { detailId = it }, onSettings = { showSettings = true })
    }
}
