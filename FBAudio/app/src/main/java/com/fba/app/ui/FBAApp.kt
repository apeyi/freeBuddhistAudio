package com.fba.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fba.app.ui.navigation.NavGraph
import com.fba.app.ui.navigation.Routes
import com.fba.app.ui.player.MiniPlayer
import com.fba.app.ui.player.PlayerViewModel
import com.fba.app.domain.model.SangharakshitaData
import com.fba.app.ui.theme.FBATheme

sealed class DeepLink {
    data class Talk(val catNum: String) : DeepLink()
    data class Series(val seriesId: String) : DeepLink()
    data class Speaker(val name: String) : DeepLink()
}

@Composable
fun FBAApp(deepLink: DeepLink? = null) {
    FBATheme {
        val navController = rememberNavController()
        val playerViewModel: PlayerViewModel = hiltViewModel()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Handle deep link navigation
        LaunchedEffect(deepLink) {
            when (deepLink) {
                is DeepLink.Talk -> navController.navigate(Routes.detail(deepLink.catNum))
                is DeepLink.Series -> {
                    val seriesTitle = SangharakshitaData.series
                        .firstOrNull { it.id == deepLink.seriesId }?.title
                    if (seriesTitle != null) {
                        navController.navigate(Routes.browseForSeries(seriesTitle))
                    }
                }
                is DeepLink.Speaker -> navController.navigate(Routes.browseForSpeaker(deepLink.name))
                null -> {}
            }
        }

        val hideBottomBar = currentRoute == Routes.PLAYER
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
                            onExpand = { navController.navigate(Routes.PLAYER) },
                        )
                        NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            selected = currentRoute == Routes.HOME,
                            onClick = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            selected = currentRoute == Routes.SEARCH,
                            onClick = {
                                navController.navigate(Routes.SEARCH) {
                                    popUpTo(Routes.HOME)
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                            label = { Text("Downloads") },
                            selected = currentRoute == Routes.DOWNLOADS,
                            onClick = {
                                navController.navigate(Routes.DOWNLOADS) {
                                    popUpTo(Routes.HOME)
                                }
                            }
                        )
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
                        navController.navigate(Routes.PLAYER)
                    },
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}
