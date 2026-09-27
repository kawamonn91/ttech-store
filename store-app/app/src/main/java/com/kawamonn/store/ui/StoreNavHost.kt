package com.kawamonn.store.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.lifecycle.compose.LifecycleResumeEffect

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "ホーム", Icons.Filled.Home),
    Tab("all", "すべて", Icons.Filled.Apps),
    Tab("search", "検索", Icons.Filled.Search),
    Tab("updates", "アップデート", Icons.Filled.SystemUpdate),
    Tab("account", "マイページ", Icons.Filled.AccountCircle),
)

@Composable
fun StoreNavHost(pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val container = LocalContainer.current
    val updateCount by container.updateChecker.updates.collectAsState()

    // ストアアプリを開いた/フォアグラウンドに戻ったたびに最新化する(12時間ごとの背景チェックとは別)
    LifecycleResumeEffect(Unit) {
        container.updateChecker.refresh()
        onPauseOrDispose {}
    }

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.route == "updates" && updateCount.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text(updateCount.size.toString()) } }) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding: PaddingValues ->
        val openApp: (String) -> Unit = { slug -> nav.navigate("detail/$slug") }
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    openApp,
                    onOpenUpdates = {
                        nav.navigate("updates") {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    padding,
                )
            }
            composable("all") { AllAppsScreen(openApp, padding) }
            composable("search") { SearchScreen(openApp, padding) }
            composable("updates") { UpdatesScreen(openApp, padding) }
            composable("account") { com.kawamonn.store.ui.account.AccountScreen(padding) }
            composable("detail/{slug}", arguments = listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
                DetailScreen(slug = entry.arguments?.getString("slug").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
    }
}
