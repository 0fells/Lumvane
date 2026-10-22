package com.lumvane.android.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumvane.android.R
import com.lumvane.android.ui.dashboard.DashboardScreen
import com.lumvane.android.ui.editor.ProfileEditorScreen
import com.lumvane.android.ui.groups.GroupsScreen
import com.lumvane.android.ui.log.LogScreen
import com.lumvane.android.ui.profiles.ProfilesScreen
import com.lumvane.android.ui.routes.RouteRulesScreen
import com.lumvane.android.ui.routes.RoutesScreen
import com.lumvane.android.ui.settings.SettingsScreen

enum class Tab(val route: String, @StringRes val title: Int, val icon: ImageVector, val compactBar: Boolean = true) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    Profiles("profiles", R.string.tab_profiles, Icons.Filled.Dns),
    Groups("groups", R.string.tab_groups, Icons.AutoMirrored.Filled.List, compactBar = false),
    Routes("routes", R.string.tab_routes, Icons.Filled.Route),
    Log("log", R.string.tab_log, Icons.AutoMirrored.Filled.Article),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

private const val EDITOR_ROUTE = "editor?id={id}&group={group}&type={type}"
private const val RULES_ROUTE = "route/{id}"

@Composable
fun LumvaneApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    fun isSelected(tab: Tab) = destination?.hierarchy?.any { it.route == tab.route } == true
    val showNav = Tab.entries.any(::isSelected)
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val goTo: (Tab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(Modifier.fillMaxSize()) {
        if (wide && showNav) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = isSelected(tab),
                        onClick = { goTo(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.title), maxLines = 1) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showNav && !wide) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.15f))) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            Tab.entries.filter { it.compactBar }.forEach { tab ->
                                NavigationBarItem(
                                    selected = isSelected(tab) || (tab == Tab.Profiles && isSelected(Tab.Groups)),
                                    onClick = { goTo(tab) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = {
                                        Text(
                                            stringResource(tab.title),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Tab.Home.route,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable(Tab.Home.route) { DashboardScreen() }
                composable(Tab.Profiles.route) {
                    ProfilesScreen(
                        onEdit = { id -> navController.navigate("editor?id=$id&group=0&type=") },
                        onNew = { type, group -> navController.navigate("editor?id=0&group=$group&type=$type") },
                        onGroups = { navController.navigate(Tab.Groups.route) },
                    )
                }
                composable(Tab.Groups.route) { GroupsScreen() }
                composable(Tab.Routes.route) { RoutesScreen(onOpen = { id -> navController.navigate("route/$id") }) }
                composable(Tab.Log.route) { LogScreen() }
                composable(Tab.Settings.route) { SettingsScreen() }

                composable(
                    EDITOR_ROUTE,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("group") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("type") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    ProfileEditorScreen(
                        id = args?.getLong("id") ?: 0L,
                        groupId = args?.getLong("group") ?: 0L,
                        type = args?.getString("type").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    RULES_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    RouteRulesScreen(
                        routeId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
