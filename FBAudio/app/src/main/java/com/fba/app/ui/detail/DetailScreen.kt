package com.fba.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.fba.app.data.local.DownloadStatus
import com.fba.app.ui.components.ErrorMessage
import com.fba.app.ui.components.LoadingIndicator
import com.fba.app.ui.components.formatDuration
import com.fba.app.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    catNum: String,
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
    onSpeakerClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    onTranscriptClick: (String) -> Unit = {},
    playerViewModel: PlayerViewModel,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

    val isThisTalkActive = playerState.currentTalk?.catNum == catNum
    val isThisTalkPlaying = isThisTalkActive && playerState.isPlaying

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.talk?.title ?: "Talk",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))
            state.error != null -> ErrorMessage(
                message = state.error!!,
                onRetry = { viewModel.loadTalk() },
                modifier = Modifier.padding(padding),
            )
            state.talk != null -> {
                val talk = state.talk!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                ) {
                    if (talk.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = talk.imageUrl,
                            contentDescription = talk.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(talk.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    // Speaker is clickable — navigates to browse-for-speaker screen
                    Text(
                        text = talk.speaker,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { if (talk.speaker.isNotBlank()) onSpeakerClick(talk.speaker) },
                    )
                    if (talk.series.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Series: ${talk.series}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable { onSeriesClick(talk.seriesHref.ifBlank { talk.series }) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (talk.durationSeconds > 0) {
                            Text(
                                formatDuration(talk.durationSeconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (talk.year > 0) {
                            Text(
                                talk.year.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (talk.genre.isNotBlank()) {
                            Text(
                                talk.genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Play / Pause button — reflects live player state
                    Button(
                        onClick = {
                            if (isThisTalkActive) playerViewModel.togglePlayPause()
                            else onPlay(catNum)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (isThisTalkPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Text(
                            if (isThisTalkPlaying) "Pause" else "Play",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Download row — download button on left, delete icon on right only when complete
                    val download = state.download
                    when (download?.status) {
                        DownloadStatus.COMPLETE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = { },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.DownloadDone, contentDescription = null)
                                    Text("Downloaded", modifier = Modifier.padding(start = 8.dp))
                                }
                                IconButton(onClick = { viewModel.deleteDownload() }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete download",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                            OutlinedButton(
                                onClick = { },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    "Downloading... ${download.progress}%",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { viewModel.startDownload() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Text("Download for offline", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }

                    // Transcript button — only shown when a transcript URL is available
                    if (talk.transcriptUrl.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onTranscriptClick(talk.transcriptUrl) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("View Transcript")
                        }
                    }

                    // Description — strip HTML tags and decode entities
                    if (talk.description.isNotBlank()) {
                        val doc = org.jsoup.Jsoup.parse(talk.description)
                        doc.select("p").prepend("\n\n")
                        doc.select("br").append("\n")
                        val description = doc.text().trim()
                        if (description.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Chapters — clickable to play, styled like player chapter list
                    if (talk.tracks.size > 1) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Chapters (${talk.tracks.size})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        talk.tracks.forEachIndexed { index, track ->
                            val isCurrentChapter = isThisTalkActive && playerState.currentTrackIndex == index
                            androidx.compose.material3.Divider()
                            androidx.compose.material3.ListItem(
                                headlineContent = {
                                    Text(
                                        track.title.ifBlank { "Chapter ${index + 1}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrentChapter) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                        color = if (isCurrentChapter)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                trailingContent = {
                                    if (track.durationSeconds > 0) {
                                        Text(
                                            formatDuration(track.durationSeconds),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    if (isThisTalkActive) {
                                        playerViewModel.playTrackByIndex(index)
                                    } else {
                                        onPlay(catNum)
                                        playerViewModel.playTrackByIndex(index)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
