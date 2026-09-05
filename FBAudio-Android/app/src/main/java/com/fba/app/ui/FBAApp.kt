package com.fba.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fba.app.FeatureFlags
import com.fba.app.domain.model.ContentSource
import com.fba.app.ui.navigation.NavGraph
import com.fba.app.ui.navigation.Routes
import com.fba.app.ui.player.MiniPlayer
import com.fba.app.ui.player.PlayerViewModel
import com.fba.app.ui.theme.FBATheme

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
        val navController = rememberNavController()
        val playerViewModel: PlayerViewModel = hiltViewModel()
        val appViewModel: AppViewModel = hiltViewModel()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
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
                        NavigationBar {
                            // Standard bottom-bar pattern: single-top + save/restore state so
                            // re-tapping a tab doesn't recreate its ViewModel (wiping e.g.
                            // search results), and switching tabs preserves each tab's state.
                            fun navigateToTab(route: String) {
                                // If the tab is already in the back stack (e.g. Downloads →
                                // talk detail), pop back to it — plain navigate+restoreState
                                // would restore the saved stack INCLUDING the detail screen,
                                // so the tap would appear to do nothing.
                                if (navController.popBackStack(route, inclusive = false)) return
                                navController.navigate(route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }

                            @Composable
                            fun tab(route: String, label: String, icon: ImageVector, onClick: () -> Unit = { navigateToTab(route) }) {
                                NavigationBarItem(
                                    icon = { Icon(icon, contentDescription = label) },
                                    // Five tabs: keep labels on one line on narrow phones / large fonts
                                    label = { Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                                    selected = currentRoute == route,
                                    onClick = onClick,
                                )
                            }

                            tab(Routes.HOME, "Home", Icons.Default.Home)
                            tab(Routes.SEARCH, "Search", Icons.Default.Search)
                            // Downloads are a membership benefit when gating is on: the tab
                            // takes non-members to Join (existing downloads stay playable
                            // from talk pages).
                            tab(Routes.DOWNLOADS, "Downloads", Icons.Default.Download) {
                                navigateToTab(if (canDownload) Routes.DOWNLOADS else Routes.JOIN)
                            }
                            tab(Routes.JOIN, "Join", Icons.Default.Favorite)
                            tab(Routes.MY_FBA, "My FBA", Icons.Default.Person)
                        }
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
