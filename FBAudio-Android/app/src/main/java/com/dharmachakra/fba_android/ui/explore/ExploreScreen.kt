package com.dharmachakra.fba_android.ui.explore

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.R
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.ui.components.Artwork
import com.dharmachakra.fba_android.ui.components.SupportFbaButton
import com.dharmachakra.fba_android.ui.components.TileArt
import com.dharmachakra.fba_android.ui.components.TileArtwork
import com.dharmachakra.fba_android.ui.player.PlaybackMath
import com.dharmachakra.fba_android.ui.theme.FbaColors

private val PageMargin = 20.dp

/**
 * Explore (v1.0 design): a long page of rows to go as deep as you like into the archive.
 * Continue listening (new listeners: Start here), New on FBA, Popular, the Digital Legacy card,
 * Latest series, Speakers, Themes, Talks in other languages, From the archive, Places, Support
 * FBA. Big-card rows show up to 6, small-card rows up to 10, each ending on a See all tile.
 */
@Composable
fun ExploreScreen(
    onItemClick: (SearchResult) -> Unit,
    onTalkClick: (String) -> Unit,
    onSourceClick: (ContentSource, String) -> Unit,
    onMenuClick: (List<String>, String) -> Unit,
    onContinueSeeAll: () -> Unit,
    onLanguagesSettings: () -> Unit,
    onDigitalLegacyClick: () -> Unit,
    onDonateClick: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Text("Explore the archive", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = FbaColors.black,
                    modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 16.dp))
            }
            if (s.isNewListener) {
                if (s.startHere.isNotEmpty()) item {
                    Row_("Start here", subtitle = "A few good places to begin.") {
                        itemsIndexed(s.startHere) { i, c ->
                            CollectionCard(c, TileArt.rainbowAt(i, s.rainbowStart), 200.dp, 134.dp) { onSourceClick(c.source, c.title) }
                        }
                    }
                }
            } else if (s.continueListening.isNotEmpty()) item {
                Row_("Continue listening", onSeeAll = onContinueSeeAll) {
                    items(s.continueListening) { r ->
                        val left = PlaybackMath.timeLeftLabel(((r.totalDurationSeconds * 1000L - r.positionMs) / 1000).toInt())
                        Column(Modifier.width(200.dp).clickable(role = Role.Button) { onTalkClick(r.catNum) }) {
                            Artwork(r.imageUrl, aspect = 200f / 134f, modifier = Modifier.size(200.dp, 134.dp).clip(RoundedCornerShape(12.dp)))
                            if (r.totalDurationSeconds > 0) {
                                LinearProgressIndicator(
                                    progress = { (r.positionMs / 1000f / r.totalDurationSeconds).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp),
                                    color = MaterialTheme.colorScheme.primary, trackColor = FbaColors.divider,
                                    strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                                )
                            }
                            CardText(r.title, left ?: r.speaker)
                        }
                    }
                }
            }
            if (s.newOnFba.isNotEmpty()) item {
                Row_("New on FBA", subtitle = "Single talks. Series are below.",
                    onSeeAll = { onSourceClick(ContentSource.ApiCollection("latest", "New on FBA"), "New on FBA") }) {
                    items(s.newOnFba) { TalkCard(it, 150.dp) { onItemClick(it) } }
                    item { SeeAllTile(150.dp) { onSourceClick(ContentSource.ApiCollection("latest", "New on FBA"), "New on FBA") } }
                }
            }
            if (s.popular.isNotEmpty()) item {
                Row_("Popular", subtitle = "Most listened to on FBA.",
                    onSeeAll = { onSourceClick(ContentSource.ApiCollection("popular", "Popular"), "Popular") }) {
                    items(s.popular) { TalkCard(it, 150.dp) { onItemClick(it) } }
                    item { SeeAllTile(150.dp) { onSourceClick(ContentSource.ApiCollection("popular", "Popular"), "Popular") } }
                }
            }
            if (s.remastered.isNotEmpty()) item {
                DigitalLegacyCard(s.remastered, onItemClick, onDigitalLegacyClick, onDonateClick)
            }
            if (s.latestSeries.isNotEmpty()) item {
                Row_("Latest series", onSeeAll = { onSourceClick(ContentSource.ApiCollection("all_series", "Series"), "Series") }) {
                    items(s.latestSeries) { TalkCard(it, 108.dp) { onItemClick(it) } }
                    item { SeeAllTile(108.dp, "All series") { onSourceClick(ContentSource.ApiCollection("all_series", "Series"), "Series") } }
                }
            }
            if (s.speakers.isNotEmpty()) item {
                Row_("Speakers", onSeeAll = { onSourceClick(ContentSource.ApiCollection("speakers", "All speakers"), "All speakers") }) {
                    items(s.speakers) { sp ->
                        Column(Modifier.width(108.dp).clickable(role = Role.Button) { onItemClick(sp) }) {
                            // Rounded squares, never circles.
                            Artwork(sp.imageUrl, modifier = Modifier.size(108.dp).clip(RoundedCornerShape(12.dp)))
                            Text(sp.title, fontSize = 14.sp, color = FbaColors.black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            if (s.themes.isNotEmpty()) item {
                Section("Themes", onSeeAll = { onMenuClick(listOf("themes"), "Themes") }) {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(2),
                        modifier = Modifier.height(212.dp),
                        contentPadding = PaddingValues(horizontal = PageMargin),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(s.themes) { i, c ->
                            TileArtwork(
                                colors = TileArt.symbolSafeAt(i, s.rainbowStart),
                                symbol = TileArt.Symbol.WHEEL,
                                wash = TileArt.washAt(i),
                                title = c.title,
                                modifier = Modifier.size(140.dp, 100.dp).clip(RoundedCornerShape(14.dp))
                                    .clickable(role = Role.Button) { onSourceClick(c.source, c.title) },
                            )
                        }
                    }
                }
            }
            if (s.languages.isNotEmpty()) item {
                Section("Talks in other languages") {
                    LazyRow(contentPadding = PaddingValues(horizontal = PageMargin), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.languages) { l ->
                            Column(
                                Modifier.width(120.dp).clip(RoundedCornerShape(12.dp)).background(FbaColors.circle)
                                    .clickable(role = Role.Button) { onSourceClick(ContentSource.Browse("/browse?lang=${l.code}"), l.nativeName) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(l.nativeName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FbaColors.black, maxLines = 1)
                                Text(l.name, fontSize = 13.sp, color = FbaColors.meta, maxLines = 1)
                            }
                        }
                    }
                    Text(
                        "Choose which languages appear across the app in Settings, Talk languages.",
                        fontSize = 13.sp, color = FbaColors.meta,
                        modifier = Modifier.padding(horizontal = PageMargin, vertical = 8.dp).clickable(onClick = onLanguagesSettings),
                    )
                }
            }
            s.archiveYear?.let { year ->
                item {
                    Section("From the archive: $year", subtitle = "A different year each visit.",
                        seeAllLabel = "All years", onSeeAll = { onSourceClick(ContentSource.ApiCollection("years", "All years"), "All years") }) {
                        LazyRow(contentPadding = PaddingValues(horizontal = PageMargin), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Column(Modifier.width(150.dp).clickable(role = Role.Button) {
                                    onSourceClick(ContentSource.Browse("/browse?y=$year"), "Talks from $year")
                                }) {
                                    Box(Modifier.size(150.dp).clip(RoundedCornerShape(12.dp))) {
                                        TileArtwork(TileArt.rainbowAt(0, s.rainbowStart), modifier = Modifier.fillMaxSize(), wash = TileArt.Wash(1.05f, 1.05f))
                                        Text("$year", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.32f),
                                            modifier = Modifier.align(Alignment.Center))
                                    }
                                    CardText("All of $year", "${s.archiveCount} talks")
                                }
                            }
                            items(s.archiveTalks) { TalkCard(it, 150.dp) { onItemClick(it) } }
                        }
                        Text("All ${s.archiveCount} talks from $year", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = PageMargin, vertical = 8.dp)
                                .clickable(role = Role.Button) { onSourceClick(ContentSource.Browse("/browse?y=$year"), "Talks from $year") })
                    }
                }
            }
            if (s.places.isNotEmpty()) item {
                Row_("Places", onSeeAll = { onSourceClick(ContentSource.ApiCollection("places", "All places"), "All places") }) {
                    items(s.places) { p ->
                        Column(Modifier.width(132.dp).clickable(role = Role.Button) { onItemClick(p) }) {
                            Artwork(p.imageUrl, modifier = Modifier.size(132.dp).clip(RoundedCornerShape(12.dp)))
                            Text(p.title, fontSize = 14.sp, color = FbaColors.black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            item { SupportFbaButton(onDonateClick, Modifier.padding(horizontal = PageMargin)) }
        }
    }
}

/** A row heading (20 Bold, optional grey subtitle, "See all" in the page colour) and its content. */
@Composable
private fun Section(
    title: String,
    subtitle: String? = null,
    seeAllLabel: String = "See all",
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = PageMargin), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                Text(seeAllLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onSeeAll).padding(vertical = 6.dp))
            }
        }
        if (subtitle != null) {
            Text(subtitle, fontSize = 13.sp, color = FbaColors.meta, modifier = Modifier.padding(horizontal = PageMargin))
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** A heading plus a horizontally scrolling row. */
@Composable
private fun Row_(
    title: String,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) = Section(title, subtitle, onSeeAll = onSeeAll) {
    LazyRow(contentPadding = PaddingValues(horizontal = PageMargin), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** Square card: list image (talks already have a square one), title, speaker. */
@Composable
private fun TalkCard(item: SearchResult, size: Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).clickable(role = Role.Button, onClick = onClick)) {
        Artwork(item.imageUrl, modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)))
        CardText(item.title, item.speaker)
    }
}

@Composable
private fun CardText(title: String, sub: String) {
    Text(title, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, color = FbaColors.black,
        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = FbaColors.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SeeAllTile(size: Dp, label: String = "See all", onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(FbaColors.circle).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun CollectionCard(c: ExploreCollection, colors: TileArt.Colors, w: Dp, h: Dp, onClick: () -> Unit) {
    Column(Modifier.width(w).clickable(role = Role.Button, onClick = onClick)) {
        TileArtwork(colors, modifier = Modifier.size(w, h).clip(RoundedCornerShape(12.dp)), symbol = TileArt.Symbol.LOTUS)
        CardText(c.title, "")
    }
}

/** The Digital Legacy: gold-bordered card with remastered talks to swipe through and the gold panel. */
@Composable
private fun DigitalLegacyCard(
    talks: List<SearchResult>,
    onItemClick: (SearchResult) -> Unit,
    onLearnMore: () -> Unit,
    onDonate: () -> Unit,
) {
    val gold = FbaColors.gold
    Column(
        Modifier.padding(horizontal = PageMargin).clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, gold.base, RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.dharmachakra_gold), null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("The Digital Legacy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black, modifier = Modifier.weight(1f))
            Text("See all", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = gold.deep,
                modifier = Modifier.clickable(role = Role.Button, onClick = onLearnMore))
        }
        Text("Sangharakshita's talks, digitally remastered so they can be heard clearly.", fontSize = 14.sp, color = FbaColors.meta,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(talks) { t ->
                Column(Modifier.width(170.dp).clickable(role = Role.Button) { onItemClick(t) }) {
                    Artwork(t.imageUrl, aspect = 170f / 112f, modifier = Modifier.size(170.dp, 112.dp).clip(RoundedCornerShape(10.dp)))
                    CardText(t.title, listOfNotNull(t.speaker.ifBlank { null }, t.year.takeIf { it > 0 }?.toString()).joinToString(" · "))
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(gold.base).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Help keep Sangharakshita's voice clear", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Each remastered talk takes a lot of time and care. It takes up to 10 hours to remaster a single lecture and get it to sound perfect! Your gift helps the Digital Legacy restore more of the archive for everyone to hear, always for free.",
                fontSize = 15.sp, lineHeight = 21.sp, color = Color.White,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Donate", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = gold.deep,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .clickable(role = Role.Button, onClick = onDonate).padding(horizontal = 20.dp, vertical = 14.dp))
                Spacer(Modifier.width(12.dp))
                Text("Learn more", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onLearnMore).padding(8.dp))
            }
        }
    }
}
