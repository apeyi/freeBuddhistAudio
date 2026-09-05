package com.fba.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fba.app.FeatureFlags
import com.fba.app.R
import com.fba.app.domain.model.ContentSource

/** External "Connect" links shown as a row of chips on Home. */
private data class ConnectLink(val label: String, val url: String, val icon: ImageVector)

private val connectLinks = listOf(
    ConnectLink("FBA podcast", "https://www.freebuddhistaudio.com/community/podcasts", Icons.Default.Podcasts),
    ConnectLink("Dharmabytes", "https://www.freebuddhistaudio.com/community/podcasts", Icons.Default.Headphones),
    ConnectLink("YouTube", "https://youtube.com/freebuddhistaudio1967", Icons.Default.OndemandVideo),
    ConnectLink("Facebook", "https://www.facebook.com/pages/Free-Buddhist-Audio/79854346331", Icons.Default.Public),
    ConnectLink("Instagram", "https://www.instagram.com/freebuddhistaudio/", Icons.Default.Public),
    ConnectLink("SoundCloud", "https://soundcloud.com/freebuddhistaudio", Icons.Default.Headphones),
    ConnectLink("The Buddhist Centre", "https://thebuddhistcentre.com/", Icons.Default.Language),
)

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
    onOpenUrl: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val auth by viewModel.authState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // --- Header: logo + name + log in/out ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.fba_wordmark),
                        contentDescription = "Free Buddhist Audio",
                        modifier = Modifier.height(32.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Free Buddhist Audio",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (FeatureFlags.AUTH) {
                        TextButton(onClick = onLoginClick) {
                            Text(if (auth.loggedIn) auth.username.ifBlank { "My account" } else "Log in")
                        }
                    }
                }
            }

            // --- Sangharakshita ---
            item {
                SangharakshitaSection(
                    talkCount = state.sangharakshitaTalkCount,
                    seriesCount = state.sangharakshitaSeriesCount,
                    onByYearClick = onSangharakshitaByYearClick,
                    onSeriesClick = onSangharakshitaSeriesClick,
                )
            }

            // --- Digital Legacy ---
            item {
                Spacer(Modifier.height(12.dp))
                DigitalLegacyCard(
                    description = state.digitalLegacy?.description
                        ?: "Digitally remastered talks — hear the Dharma renewed for future generations.",
                    onClick = onDigitalLegacyClick,
                    onSupportClick = onDonateClick,
                )
            }

            // --- Collections ---
            item {
                Spacer(Modifier.height(12.dp))
                CollectionsCard(onClick = onCollectionsClick)
            }

            // --- Browse rows ---
            item {
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HomeRow("Introductions", "Get started with Buddhism and meditation") {
                        onSourceClick(ContentSource.ApiCollection("introductions", "Introductions"), "Introductions")
                    }
                    HomeRow("Meditations", "Guided meditations to practise with") {
                        onSourceClick(ContentSource.NamedCollection("guided-meditations"), "Meditations")
                    }
                    HomeRow("Latest", "Newly added talks") {
                        onSourceClick(ContentSource.ApiCollection("latest", "Latest"), "Latest")
                    }
                    HomeRow("Themes", "Curated collections by topic") { onMenuClick(listOf("themes"), "Themes") }
                    HomeRow("Series", "Talks that belong together") {
                        onSourceClick(ContentSource.ApiCollection("all_series", "Series"), "Series")
                    }
                    HomeRow("People", "Browse by speaker") { onMenuClick(listOf("people"), "People") }
                    HomeRow("Places", "Browse by centre and retreat centre") { onMenuClick(listOf("places"), "Places") }
                }
            }

            // --- Support FBA ---
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDonateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text("Support FBA") }
            }

            // --- Connect ---
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Connect",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(connectLinks.size) { i ->
                        val link = connectLinks[i]
                        AssistChip(
                            onClick = { onOpenUrl(link.url) },
                            label = { Text(link.label) },
                            leadingIcon = { Icon(link.icon, contentDescription = null, modifier = Modifier.height(18.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun SangharakshitaSection(
    talkCount: Int,
    seriesCount: Int,
    onByYearClick: () -> Unit,
    onSeriesClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Image(
                painter = painterResource(R.drawable.sangharakshita),
                contentDescription = "Sangharakshita",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sangharakshita", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "$talkCount talks · $seriesCount series",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onByYearClick)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Year", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSeriesClick)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Series", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DigitalLegacyCard(description: String, onClick: () -> Unit, onSupportClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2117)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "The Digital Legacy",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFDBAF55),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFEDE0D8),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSupportClick) { Text("Support the Digital Legacy") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClick) { Text("Learn more", color = Color(0xFFDBAF55)) }
            }
        }
    }
}

@Composable
private fun CollectionsCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.SelfImprovement, contentDescription = null, tint = Color.White, modifier = Modifier.height(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Collections", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "The Buddha · Meditation & Mindfulness · Living a Buddhist Life · Ethics · Wisdom…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
        }
    }
}
