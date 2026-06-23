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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.fba.app.data.local.DownloadStatus
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.fba.app.ui.components.safeFraction
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    onSpeakerClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
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
        // Minimal top bar — back, info, download. Sized like a Material3 TopAppBar
        // (64dp tall, 4dp leading inset) so the back icon lines up with every
        // other screen's toolbar back button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Series link
            if (talk?.series?.isNotBlank() == true) {
                Text(
                    text = talk.series,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable {
                        onSeriesClick(talk.seriesHref.ifBlank { talk.series })
                    },
                )
            }
            // Chapter info
            if (hasMultipleTracks) {
                Text(
                    text = "Chapter ${state.currentTrackIndex + 1} of ${tracks.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Reconnecting feedback during auto-retry (otherwise the player looks
            // dead while it waits out the retry backoff)
            if (state.isReconnecting && state.playbackError == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Reconnecting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Playback error + retry
            state.playbackError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { playerViewModel.retry() }) {
                    Text("Retry")
                }
            }
        }

        // Seek bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            // While the user drags, the slider follows the finger only — re-deriving
            // it from state.currentPosition (which ticks every 500ms during playback)
            // would yank the thumb back mid-drag and discard the drag target.
            var isDragging by remember { mutableStateOf(false) }
            var dragPosition by remember { mutableFloatStateOf(0f) }
            val livePosition = (if (state.duration > 0)
                state.currentPosition.toFloat() / state.duration else 0f).safeFraction()
            val sliderPosition = if (isDragging) dragPosition else livePosition

            Slider(
                value = sliderPosition.safeFraction(),
                onValueChange = {
                    isDragging = true
                    dragPosition = it
                },
                onValueChangeFinished = {
                    playerViewModel.seekTo((dragPosition * state.duration).toLong())
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
                thumb = {},
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        thumbTrackGapSize = 0.dp,
                        drawStopIndicator = null,
                    )
                },
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

            Box(contentAlignment = Alignment.Center) {
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
                // Spinner ring around the button while the stream buffers, so
                // "playing icon but momentarily silent" reads as loading, not stuck.
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 2.dp,
                    )
                }
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
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chapters (${tracks.size})")
                }
            }
        }

        // Speed slider — shown inline when tapped
        if (showSpeedSlider) {
            var speedSliderPos by remember(state.playbackSpeed) {
                mutableFloatStateOf(((state.playbackSpeed - 0.5f) / 1.5f).safeFraction()) // 0.5-2.0 → 0-1
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
                    value = speedSliderPos.safeFraction(),
                    onValueChange = {
                        speedSliderPos = it
                        // Apply speed live while sliding
                        val speed = 0.5f + it * 1.5f
                        val snapped = (Math.round(speed * 20.0) / 20.0).toFloat()
                        playerViewModel.setPlaybackSpeed(snapped)
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    thumb = {},
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(4.dp),
                            thumbTrackGapSize = 0.dp,
                            drawStopIndicator = null,
                        )
                    },
                )
                Text("2.0x", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    // Chapter list bottom sheet
    if (showTrackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTrackSheet = false },
            sheetState = sheetState,
        ) {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                itemsIndexed(tracks) { index, track ->
                    val isCurrentTrack = index == state.currentTrackIndex
                    HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text(
                                track.title.ifBlank { "Chapter ${index + 1}" },
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
