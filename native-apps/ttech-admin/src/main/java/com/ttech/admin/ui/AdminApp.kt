package com.ttech.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ttech.admin.AdminContainer

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("ホーム", Icons.Filled.Dashboard),
    Reports("報告", Icons.Filled.Flag),
    Users("ユーザー", Icons.Filled.Group),
    Diary("投稿", Icons.Filled.Notes),
    More("その他", Icons.Filled.MoreHoriz),
}

@Composable
fun AdminApp(container: AdminContainer) {
    val session by container.auth.session.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(scope, snackbar, container) }

    CompositionLocalProvider(LocalContainer provides container, LocalActions provides actions) {
        if (session == null) LoginScreen() else MainScaffold(snackbar)
    }
}

@Composable
private fun MainScaffold(snackbar: SnackbarHostState) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // ユーザーの詳細は、どのタブからでも開ける(戻るボタンで一覧に戻る)
    var openUserId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = openUserId != null) { openUserId = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && openUserId == null,
                        onClick = { tab = t; openUserId = null },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val userId = openUserId
            if (userId != null) {
                UserDetailScreen(userId = userId, onBack = { openUserId = null }, onOpenUser = { openUserId = it })
            } else {
                when (tab) {
                    Tab.Home -> HomeScreen(onOpenReports = { tab = Tab.Reports }, onOpenUsers = { tab = Tab.Users }, onOpenMore = { tab = Tab.More })
                    Tab.Reports -> ReportsScreen(onOpenUser = { openUserId = it })
                    Tab.Users -> UsersScreen(onOpenUser = { openUserId = it })
                    Tab.Diary -> DiaryScreen(onOpenUser = { openUserId = it })
                    Tab.More -> MoreScreen()
                }
            }
        }
    }
}
