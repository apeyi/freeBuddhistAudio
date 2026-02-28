package com.fba.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fba.app.ui.browse.BrowseScreen
import com.fba.app.ui.detail.DetailScreen
import com.fba.app.ui.downloads.DownloadsScreen
import com.fba.app.ui.home.HomeScreen
import com.fba.app.ui.player.PlayerScreen
import com.fba.app.ui.player.PlayerViewModel
import com.fba.app.ui.search.SearchScreen
import com.fba.app.ui.transcript.TranscriptScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    onPlayTalk: (String) -> Unit,
    playerViewModel: PlayerViewModel,
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBrowseClick = { navController.navigate(Routes.BROWSE) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
            )
        }
        composable(Routes.BROWSE) {
            BrowseScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("catNum") { type = NavType.StringType })
        ) { backStackEntry ->
            val catNum = backStackEntry.arguments?.getString("catNum") ?: return@composable
            DetailScreen(
                catNum = catNum,
                onPlay = onPlayTalk,
                onBack = { navController.popBackStack() },
                onSpeakerClick = { speakerName ->
                    navController.navigate(Routes.browseForSpeaker(speakerName))
                },
                onSeriesClick = { seriesName ->
                    navController.navigate(Routes.browseForSeries(seriesName))
                },
                onTranscriptClick = { url ->
                    navController.navigate(Routes.transcript(url))
                },
                playerViewModel = playerViewModel,
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { catNum ->
                    navController.navigate(Routes.detail(catNum))
                },
                onSpeakerClick = { speakerName ->
                    navController.navigate(Routes.browseForSpeaker(speakerName))
                },
                playerViewModel = playerViewModel,
            )
        }
        composable(
            route = Routes.BROWSE_SPEAKER,
            arguments = listOf(navArgument("speakerName") { type = NavType.StringType })
        ) {
            BrowseScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.BROWSE_SERIES,
            arguments = listOf(navArgument("seriesName") { type = NavType.StringType })
        ) {
            BrowseScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.TRANSCRIPT,
            arguments = listOf(navArgument("transcriptUrl") { type = NavType.StringType })
        ) {
            TranscriptScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
