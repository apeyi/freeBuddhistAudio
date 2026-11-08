package com.dharmachakra.fba_android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dharmachakra.fba_android.ui.theme.FbaColors
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dharmachakra.fba_android.FeatureFlags
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.ui.navigation.DONATE_URL
import com.dharmachakra.fba_android.ui.navigation.NavGraph
import com.dharmachakra.fba_android.ui.navigation.Routes
import com.dharmachakra.fba_android.ui.player.MiniPlayer
import com.dharmachakra.fba_android.ui.player.PlayerViewModel
import com.dharmachakra.fba_android.ui.theme.FBATheme

sealed class DeepLink {
    data class Talk(val catNum: String) : DeepLink()
    data class Series(val seriesId: String) : DeepLink()
    data class Speaker(val name: String) : DeepLink()
}

@Composable
fun FBAApp(
    deepLink: DeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    FBATheme {
        val context = LocalContext.current
        val navController = rememberNavController()
        val playerViewModel: PlayerViewModel = hiltViewModel()
        val appViewModel: AppViewModel = hiltViewModel()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        // The active tab is the section the screen belongs to: the nearest tab in the back
        // stack (a talk opened from Search keeps Search active; deep links fall back to Home).
        val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
        val currentTab = backStack.lastOrNull { it.destination.route in TAB_ROUTES }?.destination?.route
        val isMember by appViewModel.isMember.collectAsStateWithLifecycle()
        val canDownload = !FeatureFlags.MEMBERSHIP_GATING || isMember

        // Handle deep link navigation — consumed exactly once, then cleared by
        // the activity so rotation/recreation doesn't re-navigate.
        LaunchedEffect(deepLink) {
            if (deepLink == null) return@LaunchedEffect
            when (deepLink) {
                is DeepLink.Talk -> navController.navigate(Routes.detail(deepLink.catNum)) {
                    launchSingleTop = true
                }
                is DeepLink.Series -> navController.navigate(
                    Routes.list(ContentSource.seriesByCatNum(deepLink.seriesId))
                ) { launchSingleTop = true }
                is DeepLink.Speaker -> navController.navigate(Routes.browseForSpeaker(deepLink.name)) {
                    launchSingleTop = true
                }
            }
            onDeepLinkConsumed()
        }

        val hideBottomBar = currentRoute == Routes.PLAYER || currentRoute == Routes.LOGIN
        val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

        // Post-playback delete prompt for downloaded talks
        if (playerState.showDeleteDownloadPrompt) {
            AlertDialog(
                onDismissRequest = { playerViewModel.dismissDeletePrompt() },
                title = { Text("Delete download?") },
                text = { Text("You've finished listening. Remove the offline files to free up space?") },
                confirmButton = {
                    TextButton(onClick = { playerViewModel.confirmDeleteAfterPlayback() }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { playerViewModel.dismissDeletePrompt() }) {
                        Text("Keep")
                    }
                },
            )
        }

        Scaffold(
            bottomBar = {
                if (!hideBottomBar) {
                    Column {
                        MiniPlayer(
                            viewModel = playerViewModel,
                            onExpand = {
                                navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                            },
                        )
                        FbaNavigationBar(
                            currentTab = currentTab,
                            navController = navController,
                            onDonate = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))) },
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NavGraph(
                    navController = navController,
                    onPlayTalk = { catNum ->
                        playerViewModel.playTalk(catNum)
                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                    },
                    onPlayChapter = { catNum, trackIndex ->
                        playerViewModel.playTalk(catNum, trackIndex)
                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                    },
                    playerViewModel = playerViewModel,
                    canDownload = canDownload,
                )
            }
        }
    }
}

private val TAB_ROUTES = setOf(Routes.HOME, Routes.EXPLORE, Routes.SEARCH, Routes.MY_FBA)

/** One tab: route, label, filled (selected) and outlined icons. */
private data class FbaTab(val route: String?, val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

/**
 * M3 navigation bar from the Android Candidate Release: Home, Explore, Donate, Search, My FBA.
 * Active indicator = accent tint, active icon filled in accent deep, others outlined, labels
 * always shown (active label bold). Donate opens the donation page and is never "selected".
 */
@Composable
private fun FbaNavigationBar(
    currentTab: String?,
    navController: androidx.navigation.NavHostController,
    onDonate: () -> Unit,
) {
    // Single-top + save/restore state so re-tapping a tab doesn't recreate its ViewModel,
    // and if the tab is already in the back stack (e.g. Search → talk), pop back to it.
    fun navigateToTab(route: String) {
        if (navController.popBackStack(route, inclusive = false)) return
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val tabs = listOf(
        FbaTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        FbaTab(Routes.EXPLORE, "Explore", Icons.Filled.GridView, Icons.Outlined.GridView),
        FbaTab(null, "Donate", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
        FbaTab(Routes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
        FbaTab(Routes.MY_FBA, "My FBA", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
    )
    val unselected = Color(0xFF4A4A4A)
    NavigationBar(containerColor = FbaColors.circle) {
        tabs.forEach { tab ->
            val selected = tab.route != null && currentTab == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (tab.route == null) onDonate() else navigateToTab(tab.route) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = {
                    Text(
                        tab.label,
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = FbaColors.black,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = unselected,
                    unselectedTextColor = unselected,
                ),
            )
        }
    }
}
