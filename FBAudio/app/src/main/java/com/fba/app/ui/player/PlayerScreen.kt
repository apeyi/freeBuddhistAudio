package com.fba.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.fba.app.data.local.DownloadStatus
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fba.app.ui.components.formatDuration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    onSpeakerClick: (String) -> Unit = {},
    playerViewModel: PlayerViewModel,
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val talk = state.currentTalk
    val tracks = talk?.tracks ?: emptyList()
    val hasMultipleTracks = tracks.size > 1

    var showSpeedSlider by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Minimal top bar — back, info, download
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            // Info button — navigate to detail screen
            if (talk != null) {
                IconButton(onClick = { onNavigateToDetail(talk.catNum) }) {
                    Icon(Icons.Default.Info, contentDescription = "Talk details")
                }
            }
            // Download button
            val dlStatus = state.downloadStatus
            when {
                dlStatus == DownloadStatus.COMPLETE -> {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = "Downloaded",
                        modifier = Modifier.padding(end = 12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                dlStatus == DownloadStatus.DOWNLOADING || dlStatus == DownloadStatus.PENDING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 12.dp).size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
                else -> {
                    IconButton(
                        onClick = { playerViewModel.downloadCurrentTalk() },
                        enabled = talk != null,
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                    }
                }
            }
        }

        // Main content — fills available space
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Album art — compact
            if (talk?.imageUrl?.isNotBlank() == true) {
                AsyncImage(
                    model = talk.imageUrl,
                    contentDescription = talk.title,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(16.dp))
            }

            // Title
            Text(
                text = talk?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            // Speaker — clickable
            Text(
                text = talk?.speaker ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val sp = talk?.speaker
                    if (!sp.isNullOrBlank()) onSpeakerClick(sp)
                },
            )
            // Track info
            if (hasMultipleTracks) {
                Text(
                    text = "Track ${state.currentTrackIndex + 1} of ${tracks.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Seek bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            var sliderPosition by remember(state.currentPosition) {
                mutableFloatStateOf(
                    if (state.duration > 0) state.currentPosition.toFloat() / state.duration
                    else 0f
                )
            }

            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    playerViewModel.seekTo((sliderPosition * state.duration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration((state.currentPosition / 1000).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = formatDuration((state.duration / 1000).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Transport controls — prev track, rewind 10s, play/pause, forward 10s, next track
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasMultipleTracks) {
                IconButton(
                    onClick = { playerViewModel.previousTrack() },
                    enabled = state.currentTrackIndex > 0,
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(32.dp))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { playerViewModel.seekBack() }) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", modifier = Modifier.size(32.dp))
                }
                Text("10s", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.width(16.dp))

            IconButton(
                onClick = { playerViewModel.togglePlayPause() },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { playerViewModel.seekForward() }) {
                    Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", modifier = Modifier.size(32.dp))
                }
                Text("10s", style = MaterialTheme.typography.labelSmall)
            }

            if (hasMultipleTracks) {
                IconButton(
                    onClick = { playerViewModel.nextTrack() },
                    enabled = state.currentTrackIndex < tracks.size - 1,
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next track", modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Bottom row: Speed + Tracks button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Speed control
            TextButton(onClick = { showSpeedSlider = !showSpeedSlider }) {
                Text("${state.playbackSpeed}x", fontSize = 14.sp)
            }

            // Tracks button
            if (hasMultipleTracks) {
                TextButton(onClick = {
                    showTrackSheet = true
                }) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tracks (${tracks.size})")
                }
            }
        }

        // Speed slider — shown inline when tapped
        if (showSpeedSlider) {
            var speedSliderPos by remember(state.playbackSpeed) {
                mutableFloatStateOf((state.playbackSpeed - 0.5f) / 1.5f) // 0.5-2.0 → 0-1
            }
            val liveSpeed = 0.5f + speedSliderPos * 1.5f
            val displaySpeed = (Math.round(liveSpeed * 20.0) / 20.0).toFloat()

            Text(
                text = "${displaySpeed}x",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("0.5x", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = speedSliderPos,
                    onValueChange = { speedSliderPos = it },
                    onValueChangeFinished = {
                        playerViewModel.setPlaybackSpeed(displaySpeed)
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("2.0x", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    // Track list bottom sheet
    if (showTrackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTrackSheet = false },
            sheetState = sheetState,
        ) {
            Text(
                text = "Tracks",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                itemsIndexed(tracks) { index, track ->
                    val isCurrentTrack = index == state.currentTrackIndex
                    Divider()
                    ListItem(
                        headlineContent = {
                            Text(
                                track.title.ifBlank { "Part ${index + 1}" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentTrack)
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
                        modifier = Modifier
                            .clickable {
                                playerViewModel.playTrackByIndex(index)
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    showTrackSheet = false
                                }
                            }
                            .then(
                                if (isCurrentTrack)
                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                else Modifier
                            ),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
