package com.dharmachakra.fba_android.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.FeatureFlags
import com.dharmachakra.fba_android.R
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.domain.model.Featured
import com.dharmachakra.fba_android.ui.components.Artwork
import com.dharmachakra.fba_android.ui.components.SupportFbaButton
import com.dharmachakra.fba_android.ui.components.TileArt
import com.dharmachakra.fba_android.ui.components.TileArtwork
import com.dharmachakra.fba_android.ui.theme.FbaColors
import kotlinx.coroutines.launch

private val PageMargin = 20.dp

private data class ConnectLink(val label: String, val url: String, val icon: Int, val vector: Boolean)

/** Connect (SPEC Home): six logos in light grey circles, spread full width. */
private val connectLinks = listOf(
    ConnectLink("The FBA Podcast", "https://www.freebuddhistaudio.com/community/podcasts", R.drawable.connect_podcast, true),
    ConnectLink("The Buddhist Centre", "https://thebuddhistcentre.com/", R.drawable.connect_tbco, false),
    ConnectLink("YouTube", "https://youtube.com/freebuddhistaudio1967", R.drawable.connect_youtube, true),
    ConnectLink("Facebook", "https://www.facebook.com/pages/Free-Buddhist-Audio/79854346331", R.drawable.connect_facebook, false),
    ConnectLink("Instagram", "https://www.instagram.com/freebuddhistaudio/", R.drawable.connect_instagram, true),
    ConnectLink("SoundCloud", "https://soundcloud.com/freebuddhistaudio", R.drawable.connect_soundcloud, false),
)

/**
 * Home (v1.0 design): header with "sign in", the featured carousel, the Sangharakshita and
 * Digital Legacy card, Collections tiles with generated artwork, Connect and Support FBA.
 */
@Composable
fun HomeScreen(
    onSangharakshitaByYearClick: () -> Unit = {},
    onSangharakshitaSeriesClick: () -> Unit = {},
    onDigitalLegacyClick: () -> Unit = {},
    onCollectionsClick: () -> Unit = {},
    onSourceClick: (ContentSource, String) -> Unit = { _, _ -> },
    onMenuClick: (List<String>, String) -> Unit = { _, _ -> },
    onDonateClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    /** Signed in: the header shows the account name and opens My FBA. */
    onAccountClick: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onTalkClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val auth by viewModel.authState.collectAsStateWithLifecycle()

    val openFeatured: (Featured) -> Unit = { post ->
        when (val t = FeaturedLink.target(post.webUrl)) {
            is FeaturedTarget.Talk -> onTalkClick(t.catNum)
            is FeaturedTarget.Series -> onSeriesClick(t.catNum)
            FeaturedTarget.DigitalLegacy -> onDigitalLegacyClick()
            is FeaturedTarget.Web -> onOpenUrl(t.url)
        }
    }
    val openTile: (TileArt.HomeTile) -> Unit = { tile ->
        when (tile.title) {
            "Introducing Buddhism" -> onMenuClick(listOf("collections", "introducing buddhism"), tile.title)
            "Meditation & mindfulness" -> onMenuClick(listOf("collections", "meditation & mindfulness"), tile.title)
            "Latest talks & meditations" -> onSourceClick(ContentSource.ApiCollection("latest", tile.title), tile.title)
            "Themes" -> onMenuClick(listOf("themes"), tile.title)
            "Series" -> onSourceClick(ContentSource.ApiCollection("all_series", tile.title), tile.title)
            "People" -> onMenuClick(listOf("people"), tile.title)
            "Places" -> onMenuClick(listOf("places"), tile.title)
            "Order talks" -> onSourceClick(ContentSource.ApiCollection("order_talks", tile.title), tile.title)
        }
    }

    Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Header(
                    signedIn = auth.loggedIn,
                    name = auth.username,
                    onSignIn = onLoginClick,
                    onAccount = onAccountClick,
                )
            }
            if (state.featured.isNotEmpty()) {
                item { FeaturedCarousel(state.featured, openFeatured) }
            }
            item {
                SangharakshitaCard(
                    talkCount = state.sangharakshitaTalkCount,
                    seriesCount = state.sangharakshitaSeriesCount,
                    legacyText = state.digitalLegacy?.description
                        ?: "Since 1967, the team at Dharmachakra has been sharing Urgyen Sangharakshita's Dharma talks with the world.",
                    onYear = onSangharakshitaByYearClick,
                    onSeries = onSangharakshitaSeriesClick,
                    onSupport = onDonateClick,
                    onLearnMore = onDigitalLegacyClick,
                )
            }
            item {
                CollectionsGrid(
                    tiles = TileArt.homeTiles.filter { !it.orderOnly || auth.isOrderMember },
                    onTile = openTile,
                )
            }
            item { Connect(onOpenUrl) }
            item { SupportFbaButton(onDonateClick, Modifier.padding(horizontal = PageMargin)) }
        }
    }
}

/** Logo, "free buddhist audio", and "sign in" (or the account name) at the content edge. */
@Composable
private fun Header(signedIn: Boolean, name: String, onSignIn: () -> Unit, onAccount: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = PageMargin, end = PageMargin, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.fba_wordmark), contentDescription = null, modifier = Modifier.height(21.dp))
        Spacer(Modifier.width(12.dp))
        Text("free buddhist audio", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = FbaColors.black,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Free Buddhist Audio" })
        if (FeatureFlags.AUTH) {
            Text(
                if (signedIn) name.ifBlank { "My account" } else "sign in",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = if (signedIn) onAccount else onSignIn)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * Featured carousel: 290 x 360 cards, image on top and a coloured band with FEATURED and the
 * title, about 80dp of the next card showing as the cue to swipe; grey dots that also move
 * between cards.
 */
@Composable
private fun FeaturedCarousel(posts: List<Featured>, onOpen: (Featured) -> Unit) {
    val pager = rememberPagerState { posts.size }
    val scope = rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pager,
            pageSize = PageSize.Fixed(290.dp),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = PageMargin),
            modifier = Modifier.fillMaxWidth().height(372.dp),
        ) { page ->
            val post = posts[page]
            Column(
                Modifier
                    .padding(vertical = 6.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(FeaturedLink.band(page)))
                    .clickable(role = Role.Button, onClickLabel = "Open") { onOpen(post) }
                    .fillMaxSize(),
            ) {
                Artwork(post.imageUrl, aspect = 290f / 200f, modifier = Modifier.fillMaxWidth().height(200.dp))
                Column(Modifier.padding(16.dp)) {
                    Text("FEATURED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f), letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(post.title, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            posts.indices.forEach { i ->
                val selected = i == pager.currentPage
                Box(
                    Modifier
                        .size(if (selected) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (selected) FbaColors.grey700 else FbaColors.grey500.copy(alpha = 0.5f))
                        .clickable(role = Role.Button, onClickLabel = "Featured ${i + 1} of ${posts.size}") {
                            scope.launch { pager.animateScrollToPage(i) }
                        },
                )
            }
        }
    }
}

/**
 * One card with a thin gold border: Sangharakshita's photo, title and counts, Year and Series
 * rows, and the gold Digital Legacy panel at its base.
 */
@Composable
private fun SangharakshitaCard(
    talkCount: Int,
    seriesCount: Int,
    legacyText: String,
    onYear: () -> Unit,
    onSeries: () -> Unit,
    onSupport: () -> Unit,
    onLearnMore: () -> Unit,
) {
    val gold = FbaColors.gold
    Column(
        Modifier
            .padding(horizontal = PageMargin)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, gold.base, RoundedCornerShape(16.dp))
            .background(FbaColors.white),
    ) {
        Image(
            painterResource(R.drawable.sangharakshita),
            contentDescription = "Sangharakshita",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text("Sangharakshita", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
            Text("$talkCount talks · $seriesCount series", fontSize = 15.sp, color = FbaColors.meta)
        }
        HorizontalDivider(color = FbaColors.divider)
        CardRow("Year", onYear)
        HorizontalDivider(color = FbaColors.divider)
        CardRow("Series", onSeries)
        Column(
            Modifier.fillMaxWidth().background(gold.base).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("The Digital Legacy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(legacyText, fontSize = 15.sp, lineHeight = 21.sp, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Support the Digital Legacy",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = gold.deep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .clickable(role = Role.Button, onClick = onSupport)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("Learn more", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onLearnMore).padding(8.dp))
            }
        }
    }
}

@Composable
private fun CardRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp, color = FbaColors.black, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FbaColors.grey700)
    }
}

/** "Collections": the eight tiles with generated artwork, two columns. */
@Composable
private fun CollectionsGrid(tiles: List<TileArt.HomeTile>, onTile: (TileArt.HomeTile) -> Unit) {
    Column(Modifier.padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Collections", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { tile ->
                    TileArtwork(
                        colors = tile.colors,
                        symbol = tile.symbol,
                        wash = tile.wash,
                        title = tile.title,
                        caption = if (tile.orderOnly) "Order members only" else null,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(174f / 104f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onTile(tile) },
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Connect(onOpenUrl: (String) -> Unit) {
    Column(Modifier.padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            connectLinks.forEach { link ->
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FbaColors.circle)
                        .clickable(role = Role.Button, onClickLabel = link.label) { onOpenUrl(link.url) }
                        .semantics { contentDescription = link.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(link.icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(FbaColors.meta),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
