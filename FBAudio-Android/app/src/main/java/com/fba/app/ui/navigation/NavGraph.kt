package com.fba.app.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fba.app.FeatureFlags
import com.fba.app.domain.model.ContentSource
import com.fba.app.domain.model.MenuNode
import com.fba.app.domain.model.SearchResult
import com.fba.app.ui.auth.LoginScreen
import com.fba.app.ui.browse.BrowseScreen
import com.fba.app.ui.collections.CollectionsScreen
import com.fba.app.ui.detail.DetailScreen
import com.fba.app.ui.downloads.DownloadsScreen
import com.fba.app.ui.home.HomeScreen
import com.fba.app.ui.join.JoinScreen
import com.fba.app.ui.legacy.DigitalLegacyScreen
import com.fba.app.ui.list.ListScreen
import com.fba.app.ui.menu.MenuListScreen
import com.fba.app.ui.myfba.MyFbaScreen
import com.fba.app.ui.player.PlayerScreen
import com.fba.app.ui.player.PlayerViewModel
import com.fba.app.ui.search.SearchScreen
import com.fba.app.ui.transcript.TranscriptScreen

const val DONATE_URL = "https://www.freebuddhistaudio.com/donate/"

@Composable
fun NavGraph(
    navController: NavHostController,
    onPlayTalk: (String) -> Unit,
    onPlayChapter: (String, Int) -> Unit = { catNum, _ -> onPlayTalk(catNum) },
    playerViewModel: PlayerViewModel,
    /** Downloads are member-only when gating is on; non-members land on Join. */
    canDownload: Boolean = true,
) {
    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    val onDonate = { openUrl(DONATE_URL) }

    /** Talks → detail; series → series list; speaker/place/year tiles → their listing. */
    val onItemClick: (SearchResult) -> Unit = { item ->
        when {
            item.isSeries -> navController.navigate(Routes.seriesFromHref(item.path))
            item.isBrowseLink -> navController.navigate(
                Routes.list(ContentSource.Browse(item.path.removePrefix("https://www.freebuddhistaudio.com")), item.title)
            )
            else -> navController.navigate(Routes.detail(item.catNum))
        }
    }

    /** Menu entries → the right screen for their link type. */
    val onMenuNodeClick: (List<String>, MenuNode) -> Unit = { parentPath, node ->
        when {
            node.hasChildren -> navController.navigate(Routes.menu(parentPath + node.label, node.label))
            node.isExternal -> openUrl(node.link)
            else -> node.toSource()?.let { navController.navigate(Routes.list(it, node.label)) }
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onSangharakshitaByYearClick = { navController.navigate(Routes.SANGHARAKSHITA_BY_YEAR) },
                onSangharakshitaSeriesClick = {
                    navController.navigate(Routes.list(ContentSource.ApiCollection("series_sangharakshita", "Series by Sangharakshita")))
                },
                onDigitalLegacyClick = { navController.navigate(Routes.DIGITAL_LEGACY) },
                onCollectionsClick = { navController.navigate(Routes.COLLECTIONS) },
                onSourceClick = { source, title -> navController.navigate(Routes.list(source, title)) },
                onMenuClick = { path, title -> navController.navigate(Routes.menu(path, title)) },
                onDonateClick = onDonate,
                onLoginClick = { navController.navigate(Routes.LOGIN) },
                onOpenUrl = openUrl,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onSeriesClick = { navController.navigate(Routes.seriesFromHref(it)) },
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
                onPlayChapter = onPlayChapter,
                onBack = { navController.popBackStack() },
                onSpeakerClick = { speakerName ->
                    navController.navigate(Routes.browseForSpeaker(speakerName))
                },
                onSeriesClick = { seriesHref -> navController.navigate(Routes.seriesFromHref(seriesHref)) },
                onTranscriptClick = { url ->
                    navController.navigate(Routes.transcript(url, catNum))
                },
                onDonateClick = onDonate,
                onJoinClick = { navController.navigate(Routes.JOIN) },
                canDownload = canDownload,
                playerViewModel = playerViewModel,
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.JOIN) {
            JoinScreen(onDonateClick = onDonate, onBack = { navController.popBackStack() })
        }
        composable(Routes.MY_FBA) {
            MyFbaScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onDonateClick = onDonate,
                onLoginClick = { navController.navigate(Routes.LOGIN) },
                onJoinClick = { navController.navigate(Routes.JOIN) },
            )
        }
        if (FeatureFlags.AUTH) {
            composable(Routes.LOGIN) {
                LoginScreen(onDone = { navController.popBackStack() })
            }
        }
        composable(Routes.COLLECTIONS) {
            CollectionsScreen(
                onCollectionClick = { node -> onMenuNodeClick(listOf("collections"), node) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DIGITAL_LEGACY) {
            DigitalLegacyScreen(
                onPlaySample = { catNum -> onPlayTalk(catNum) },
                onSeriesClick = { path -> navController.navigate(Routes.list(ContentSource.Series(path))) },
                onDonateClick = onDonate,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.LIST,
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            )
        ) {
            ListScreen(
                onItemClick = onItemClick,
                onDonateClick = onDonate,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.MENU,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            )
        ) { backStackEntry ->
            val path = (backStackEntry.arguments?.getString("path") ?: "").split('|').filter { it.isNotBlank() }
            MenuListScreen(
                onNodeClick = { node -> onMenuNodeClick(path, node) },
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
                onSeriesClick = { seriesHref -> navController.navigate(Routes.seriesFromHref(seriesHref)) },
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
                alwaysPopOnBack = true,
            )
        }
        composable(
            route = Routes.BROWSE_SERIES,
            arguments = listOf(navArgument("seriesName") { type = NavType.StringType })
        ) {
            BrowseScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
                alwaysPopOnBack = true,
            )
        }
        composable(
            route = Routes.TRANSCRIPT,
            arguments = listOf(
                navArgument("transcriptUrl") { type = NavType.StringType },
                navArgument("catNum") { type = NavType.StringType; defaultValue = "" },
            )
        ) {
            TranscriptScreen(
                onBack = { navController.popBackStack() },
            )
        }
        // Sangharakshita by year — reuses BrowseScreen with pre-selected category
        composable(Routes.SANGHARAKSHITA_BY_YEAR) {
            BrowseScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
                initialSangharakshitaByYear = true,
            )
        }
        // Sangharakshita series list (legacy hardcoded list; Home now uses the website list with images)
        composable(Routes.SANGHARAKSHITA_SERIES) {
            BrowseScreen(
                onTalkClick = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
                initialSangharakshitaSeries = true,
            )
        }
    }
}
