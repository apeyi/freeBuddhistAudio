package com.fba.app.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fba.app.ui.components.TalkCard
import com.fba.app.ui.components.formatDuration

@Composable
fun HomeScreen(
    onTalkClick: (String) -> Unit,
    onBrowseClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSangharakshitaByYearClick: () -> Unit = {},
    onSangharakshitaSeriesClick: (String) -> Unit = {},
    onMitraStudyClick: () -> Unit = {},
    onDonateClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // --- Sangharakshita section ---
            item {
                SangharakshitaSection(
                    imageUrl = state.sangharakshitaImageUrl,
                    talkCount = state.sangharakshitaTalkCount,
                    seriesCount = state.sangharakshitaSeriesCount,
                    onByYearClick = onSangharakshitaByYearClick,
                    onSeriesClick = onSangharakshitaSeriesClick,
                )
            }

            // --- Mitra Study section ---
            item {
                Spacer(Modifier.height(8.dp))
                SectionCard(
                    title = "Mitra Study",
                    subtitle = "Structured study courses",
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(32.dp)) },
                    onClick = onMitraStudyClick,
                )
            }

            // --- Support FBA ---
            item {
                Spacer(Modifier.height(8.dp))
                DonateCard(onDonateClick = onDonateClick)
            }

            // --- Recently Listened ---
            if (state.recentlyListened.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Recently Listened",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.recentlyListened, key = { it.catNum }) { entry ->
                    val totalMs = entry.totalDurationSeconds * 1000L
                    val progress = if (totalMs > 0)
                        (entry.positionMs.toFloat() / totalMs).coerceIn(0f, 1f)
                    else 0f
                    val subtitle = if (entry.totalDurationSeconds > 0) {
                        val posText = formatDuration((entry.positionMs / 1000).toInt())
                        val totalText = formatDuration(entry.totalDurationSeconds)
                        "$posText / $totalText"
                    } else null
                    val isDownloaded = entry.catNum in state.downloadedCatNums

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        TalkCard(
                            title = entry.title,
                            speaker = entry.speaker,
                            imageUrl = entry.imageUrl,
                            subtitle = subtitle,
                            onClick = { onTalkClick(entry.catNum) },
                            trailing = if (isDownloaded) {
                                {
                                    Icon(
                                        Icons.Default.DownloadDone,
                                        contentDescription = "Downloaded",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else null,
                        )
                        if (progress > 0f) {
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .height(3.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SangharakshitaSection(
    imageUrl: String,
    talkCount: Int,
    seriesCount: Int,
    onByYearClick: () -> Unit,
    onSeriesClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Hero image
            AsyncImage(
                model = imageUrl,
                contentDescription = "Sangharakshita",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop,
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sangharakshita",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "$talkCount talks · $seriesCount series",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // By Year subsection
                ListItem(
                    headlineContent = { Text("By Year") },
                    supportingContent = { Text("Browse all talks by decade and year") },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onByYearClick),
                )

                // Series subsection
                ListItem(
                    headlineContent = { Text("Series") },
                    supportingContent = { Text("$seriesCount lecture series") },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSeriesClick("sang://series") },
                )
            }
        }
    }
}

@Composable
private fun DonateCard(onDonateClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onDonateClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = "https://www.freebuddhistaudio.com/images/logo/fba-half-size.jpg",
                contentDescription = "FBA logo",
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Support Free Buddhist Audio", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Donate to help keep FBA free for everyone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
