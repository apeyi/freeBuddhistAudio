package com.dharmachakra.fba_android.ui.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dharmachakra.fba_android.R
import com.dharmachakra.fba_android.data.local.DownloadStatus
import com.dharmachakra.fba_android.domain.model.Talk
import com.dharmachakra.fba_android.player.SleepOption
import com.dharmachakra.fba_android.ui.components.ArtworkRule
import com.dharmachakra.fba_android.ui.components.TalkArtwork
import com.dharmachakra.fba_android.ui.components.formatDuration
import com.dharmachakra.fba_android.ui.components.safeFraction
import com.dharmachakra.fba_android.ui.detail.TalkProgress
import com.dharmachakra.fba_android.ui.theme.FbaColors
import com.dharmachakra.fba_android.ui.theme.LocalPageColors
import com.dharmachakra.fba_android.ui.theme.PageColorScope
import com.dharmachakra.fba_android.ui.theme.PageTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val White72 = Color.White.copy(alpha = 0.72f)
private val White88 = Color.White.copy(alpha = 0.88f)
private val White18 = Color.White.copy(alpha = 0.18f)
private val Mute = Color(0xFF6B6661)

/** Speeds offered (SPEC "Playback speed sheet"). */
private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(speed: Float): String {
    val s = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
    return "$s×"
}

/**
 * Full-screen player (v1.0 design): coloured from the artwork (hourly colour for black-and-white
 * art, gold on remastered talks), "Playing from", artwork, title, speaker, the current track
 * scrolling sideways when long, scrubber, transport (back 15 / previous / play / next / forward
 * 30; one-track talks swap previous and next for speed and sleep), the talk tools row, and white
 * sheets for More, Playback speed, Sleep timer and Tracks. Swipe down to close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    onSpeakerClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    onTranscriptClick: (String, String) -> Unit = { _, _ -> },
    onDonateClick: () -> Unit = {},
    onPlayingFromClick: (String) -> Unit = {},
    playerViewModel: PlayerViewModel,
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val sleep by playerViewModel.sleepTimerState.collectAsStateWithLifecycle()
    val talk = state.currentTalk ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val tracks = talk.tracks
    val multiTrack = tracks.size > 1
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }

    // Light status bar icons on the coloured background while the player is open.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (wasLight != null) controller.isAppearanceLightStatusBars = wasLight }
    }

    PageColorScope(if (talk.hasRemaster) PageTheme.REMASTERED else PageTheme.HOURLY) {
        val gradient = rememberPlayerGradient(talk)
        val dragY = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val closeThreshold = with(LocalDensity.current) { 140.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragY.value.roundToInt().coerceAtLeast(0)) }
                .background(Brush.verticalGradient(0f to Color(gradient.top), 0.45f to Color(gradient.mid), 1f to Color(gradient.bottom)))
                .background(Mute.copy(alpha = 0.15f))
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.05f), 0.6f to Color.Black.copy(alpha = 0.1f), 1f to Color.Black.copy(alpha = 0.3f)))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (dragY.value > closeThreshold) onBack() else dragY.animateTo(0f)
                            }
                        },
                    ) { _, dy -> scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) } }
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                // ---- Header: close, Playing from ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(White18),
                    ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player", tint = Color.White) }
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(enabled = state.playingFromRoute != null) {
                                state.playingFromRoute?.let(onPlayingFromClick)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state.playingFrom.isNotBlank()) {
                            Text("PLAYING FROM", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = White72, letterSpacing = 0.66.sp)
                            Text(state.playingFrom, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        }
                    }
                    Spacer(Modifier.size(40.dp))
                }

                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    TalkArtwork(
                        talk = talk,
                        aspect = 372f / 272f,
                        modifier = Modifier.fillMaxWidth().height(272.dp).clip(RoundedCornerShape(16.dp)),
                    )

                    TitleBlock(talk, state, multiTrack, onSpeakerClick, playerViewModel)

                    Scrubber(
                        positionMs = state.currentPosition,
                        durationMs = state.duration,
                        onSeek = { playerViewModel.seekTo(it) },
                    )

                    Transport(
                        isPlaying = state.isPlaying,
                        isBuffering = state.isBuffering,
                        multiTrack = multiTrack,
                        hasPrevious = state.currentTrackIndex > 0,
                        hasNext = state.currentTrackIndex < tracks.size - 1,
                        playColor = Color(gradient.top),
                        speed = state.playbackSpeed,
                        sleepOn = sleep.option != SleepOption.OFF,
                        onBack15 = { playerViewModel.seekBack() },
                        onForward30 = { playerViewModel.seekForward() },
                        onPrevious = { playerViewModel.previousTrack() },
                        onNext = { playerViewModel.nextTrack() },
                        onPlayPause = { playerViewModel.togglePlayPause() },
                        onSpeed = { sheet = PlayerSheet.SPEED },
                        onSleep = { sheet = PlayerSheet.SLEEP },
                    )

                    if (state.isReconnecting && state.playbackError == null) {
                        Text("Reconnecting", fontSize = 14.sp, color = White88, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    state.playbackError?.let { err ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(err, fontSize = 14.sp, color = Color.White)
                            TextButton(onClick = { playerViewModel.retry() }) { Text("Try again", color = Color.White) }
                        }
                    }

                    ToolsRow(
                        talk = talk,
                        downloadStatus = state.downloadStatus,
                        onDownload = { if (state.downloadStatus == null) playerViewModel.downloadCurrentTalk() },
                        onTranscript = { onTranscriptClick(talk.transcriptUrl, talk.catNum) },
                        onDonate = onDonateClick,
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, talk.title)
                                putExtra(Intent.EXTRA_TEXT, "${talk.title} by ${talk.speaker}\n${talkUrl(talk.catNum)}")
                            }
                            context.startActivity(Intent.createChooser(send, "Share talk"))
                        },
                        onMore = { sheet = PlayerSheet.MORE },
                    )
                }
            }
        }

        when (sheet) {
            PlayerSheet.MORE -> MoreSheet(
                talk = talk,
                multiTrack = multiTrack,
                speed = state.playbackSpeed,
                sleep = sleep.option,
                onDismiss = { sheet = null },
                onSpeed = { sheet = PlayerSheet.SPEED },
                onSleep = { sheet = PlayerSheet.SLEEP },
                onTracks = { sheet = PlayerSheet.TRACKS },
                onTalkPage = { sheet = null; onNavigateToDetail(talk.catNum) },
                onSeries = { sheet = null; onSeriesClick(talk.seriesHref.ifBlank { talk.series }) },
                onSpeaker = { sheet = null; onSpeakerClick(talk.speaker) },
                onCopyLink = { clipboard.setText(AnnotatedString(talkUrl(talk.catNum))); sheet = null },
                onReport = { sheet = PlayerSheet.REPORT },
            )
            PlayerSheet.REPORT -> com.dharmachakra.fba_android.ui.components.ReportProblemSheet(
                talk = talk,
                positionSeconds = (PlaybackMath.cumulativePositionMs(tracks, state.currentTrackIndex, state.currentPosition) / 1000).toInt(),
                onDismiss = { sheet = null },
            )
            PlayerSheet.SPEED -> ChoiceSheet(
                title = "Playback speed",
                subtitle = "Changes the pace, not the pitch.",
                options = SPEEDS.map { (if (it == 1f) "1× (normal)" else speedLabel(it)) to it },
                selected = state.playbackSpeed,
                onSelect = { playerViewModel.setPlaybackSpeed(it); sheet = null },
                onDismiss = { sheet = null },
            )
            PlayerSheet.SLEEP -> ChoiceSheet(
                title = "Sleep timer",
                subtitle = "Pauses playback gently, with a short fade out.",
                options = SleepOption.entries
                    .filter { multiTrack || it != SleepOption.END_OF_TRACK }
                    .map { it.label to it },
                selected = sleep.option,
                onSelect = { playerViewModel.setSleepTimer(it); sheet = null },
                onDismiss = { sheet = null },
            )
            PlayerSheet.TRACKS -> TracksSheet(
                talk = talk,
                currentTrack = state.currentTrackIndex,
                positionMs = state.currentPosition,
                onTrack = { playerViewModel.playTrackByIndex(it); sheet = null },
                onDismiss = { sheet = null },
            )
            null -> Unit
        }
    }
}

private enum class PlayerSheet { MORE, SPEED, SLEEP, TRACKS, REPORT }

private fun talkUrl(catNum: String) = "https://www.freebuddhistaudio.com/audio/details?num=${Uri.encode(catNum)}"

/** The gradient for this talk: gold on remasters, else from the artwork, else the hourly colour. */
@Composable
private fun rememberPlayerGradient(talk: Talk): PlayerColors.Gradient {
    val accent = LocalPageColors.current.set
    val fallback = if (talk.hasRemaster) PlayerColors.forGold() else PlayerColors.forAccent(accent)
    if (talk.hasRemaster) return fallback
    val url = ArtworkRule.talkImage(talk.bannerUrl, talk.speakerImageUrl, talk.imageUrl)
    val context = LocalContext.current
    val extracted by produceState<PlayerColors.Gradient?>(null, url) {
        if (url == null) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).allowHardware(false).size(160).build()
                ) as? SuccessResult ?: return@runCatching null
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching null
                val p = Palette.from(bitmap).maximumColorCount(24).generate()
                // Every swatch, not just the five named ones, so a second hue (e.g. the blue in
                // LOC7132's banner) is available for the bottom of the gradient.
                PlayerColors.fromColors(p.swatches.filter { it.population > 40 }.map { it.rgb })
            }.getOrNull()
        }
    }
    return extracted ?: fallback
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleBlock(
    talk: Talk,
    state: PlayerUiState,
    multiTrack: Boolean,
    onSpeakerClick: (String) -> Unit,
    vm: PlayerViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(talk.title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Color.White,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (talk.speaker.isNotBlank()) {
            Text(
                talk.speaker,
                fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(role = Role.Button) { onSpeakerClick(talk.speaker) },
            )
        }
        if (talk.hasRemaster) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Image(painterResource(R.drawable.dharmachakra_gold), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("digitally remastered", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = White88,
                    textDecoration = TextDecoration.Underline)
            }
        }
        val track = talk.tracks.getOrNull(state.currentTrackIndex)
        if (multiTrack && track != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Track ${state.currentTrackIndex + 1} of ${talk.tracks.size} · ${track.title.ifBlank { "Track ${state.currentTrackIndex + 1}" }}",
                fontSize = 16.sp, fontWeight = FontWeight.Medium, color = White88, maxLines = 1,
                modifier = Modifier.fillMaxWidth().basicMarquee(initialDelayMillis = 2_000, repeatDelayMillis = 2_000),
            )
        }
        if (talk.hasRemaster) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Original", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (!state.useRemaster) Color.White else White72)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.useRemaster,
                    onCheckedChange = { vm.setUseRemaster(it) },
                    enabled = !state.versionLocked,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color.White.copy(alpha = 0.35f),
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                        uncheckedThumbColor = White88,
                        uncheckedBorderColor = White72,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Text("Enhanced", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (state.useRemaster) Color.White else White72)
            }
            if (state.versionLocked) {
                Text("Playing the downloaded version", fontSize = 12.sp, color = White72, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

/** 4dp track (white 30%), elapsed white, 14dp handle; elapsed left, time remaining right. */
@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val live = (if (durationMs > 0) positionMs.toFloat() / durationMs else 0f).safeFraction()
    val fraction = if (dragging) dragFraction else live
    val shownPos = if (dragging) (dragFraction * durationMs).toLong() else positionMs
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .semantics {
                    contentDescription = "Playback position"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { onSeek((it * durationMs).toLong()); true }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong()) }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { if (durationMs > 0) onSeek((dragFraction * durationMs).toLong()); dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f)))
            Box(Modifier.width(w * fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            Box(Modifier.offset(x = (w - 14.dp) * fraction).size(14.dp).clip(CircleShape).background(Color.White))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((shownPos / 1000).toInt()), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = White72)
            val remaining = ((durationMs - shownPos) / 1000).toInt().coerceAtLeast(0)
            Text("−" + formatDuration(remaining), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = White72)
        }
    }
}

@Composable
private fun Transport(
    isPlaying: Boolean,
    isBuffering: Boolean,
    multiTrack: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    playColor: Color,
    speed: Float,
    sleepOn: Boolean,
    onBack15: () -> Unit,
    onForward30: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiTrack) {
            SkipButton(seconds = 15, forward = false, onClick = onBack15)
            IconButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(Icons.Default.SkipPrevious, "Previous track", tint = if (hasPrevious) Color.White else White72, modifier = Modifier.size(32.dp))
            }
        } else {
            TextButton(onClick = onSpeed) { Text(speedLabel(speed), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            SkipButton(seconds = 15, forward = false, onClick = onBack15)
        }
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(role = Role.Button, onClickLabel = if (isPlaying) "Pause" else "Play", onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = playColor,
                    modifier = Modifier.size(40.dp),
                )
            }
            if (isBuffering) CircularProgressIndicator(Modifier.size(84.dp), color = Color.White, strokeWidth = 2.dp)
        }
        if (multiTrack) {
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(Icons.Default.SkipNext, "Next track", tint = if (hasNext) Color.White else White72, modifier = Modifier.size(32.dp))
            }
            SkipButton(seconds = 30, forward = true, onClick = onForward30)
        } else {
            SkipButton(seconds = 30, forward = true, onClick = onForward30)
            IconButton(onClick = onSleep) {
                Icon(Icons.Default.Bedtime, contentDescription = if (sleepOn) "Sleep timer on" else "Sleep timer",
                    tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Circular arrow with the seconds inside (back 15, forward 30). */
@Composable
private fun SkipButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Replay,
                contentDescription = if (forward) "Forward $seconds seconds" else "Back $seconds seconds",
                tint = Color.White,
                modifier = Modifier.size(36.dp).scale(scaleX = if (forward) -1f else 1f, scaleY = 1f),
            )
            Text("$seconds", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun ToolsRow(
    talk: Talk,
    downloadStatus: DownloadStatus?,
    onDownload: () -> Unit,
    onTranscript: () -> Unit,
    onDonate: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        when (downloadStatus) {
            DownloadStatus.COMPLETE -> WhiteTool("Downloaded", Icons.Default.DownloadDone, onDownload)
            DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> WhiteTool("Downloading", null, onDownload) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            }
            else -> WhiteTool("Download", Icons.Outlined.FileDownload, onDownload)
        }
        if (talk.transcriptUrl.isNotBlank()) WhiteTool("Transcript", Icons.Outlined.Description, onTranscript)
        WhiteTool("Donate", Icons.Default.Favorite, onDonate)   // white heart on the coloured player
        WhiteTool("Share", Icons.Default.Share, onShare)
        WhiteTool("More", Icons.Default.MoreVert, onMore, contentDescription = "More options")
    }
}

@Composable
private fun WhiteTool(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    contentDescription: String = label,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(White18), contentAlignment = Alignment.Center) {
            if (content != null) content() else if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

/** More (SPEC 7 Oct): speed, sleep and tracks (multi-track talks), then the talk options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    talk: Talk,
    multiTrack: Boolean,
    speed: Float,
    sleep: SleepOption,
    onDismiss: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onTracks: () -> Unit,
    onTalkPage: () -> Unit,
    onSeries: () -> Unit,
    onSpeaker: () -> Unit,
    onCopyLink: () -> Unit,
    onReport: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            @Composable fun row(label: String, value: String? = null, action: () -> Unit) = ListItem(
                headlineContent = { Text(label) },
                trailingContent = value?.let { { Text(it, color = FbaColors.meta) } },
                modifier = Modifier.clickable(role = Role.Button, onClick = action),
            )
            if (multiTrack) {
                row("Playback speed", speedLabel(speed), onSpeed)
                row("Sleep timer", sleep.shortLabel, onSleep)
                row("Tracks", "${talk.tracks.size}", onTracks)
                HorizontalDivider(color = FbaColors.divider)
            }
            row("Go to talk page", action = onTalkPage)
            if (talk.series.isNotBlank()) row("Go to series", action = onSeries)
            if (talk.speaker.isNotBlank()) row("Go to speaker", action = onSpeaker)
            row("Copy link", action = onCopyLink)
            row("Report a problem", action = onReport)
        }
    }
}

/** A white sheet with a title, a one-line explanation and ticked choices (speed, sleep). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    subtitle: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            Text(subtitle, fontSize = 14.sp, color = FbaColors.meta, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            options.forEach { (label, value) ->
                val isSelected = value == selected
                ListItem(
                    headlineContent = { Text(label, color = if (isSelected) accent else FbaColors.black,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal) },
                    trailingContent = if (isSelected) ({ Icon(Icons.Default.Check, "Selected", tint = accent) }) else null,
                    modifier = Modifier.clickable(role = Role.Button) { onSelect(value) },
                )
            }
        }
    }
}

/** Tracks sheet: opens at half height scrolled to the current track; ticks for tracks heard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TracksSheet(talk: Talk, currentTrack: Int, positionMs: Long, onTrack: (Int) -> Unit, onDismiss: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentTrack - 1).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Text("Tracks (${talk.tracks.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(talk.tracks) { index, track ->
                if (index > 0) HorizontalDivider(color = FbaColors.divider, modifier = Modifier.padding(horizontal = 24.dp))
                val current = index == currentTrack
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onTrack(index) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (index < currentTrack) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, "Listened", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    } else {
                        Text("${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FbaColors.grey500)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(track.title.ifBlank { "Track ${index + 1}" }, fontSize = 16.sp,
                            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                            color = if (current) accent else FbaColors.black)
                        if (current && track.durationSeconds > 0) {
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(FbaColors.divider)) {
                                Box(Modifier.fillMaxWidth(TalkProgress.trackFraction(track, positionMs)).height(4.dp).background(accent))
                            }
                        }
                    }
                    val trailing = when {
                        index < currentTrack -> "Listened"
                        current -> PlaybackMath.timeLeftLabel(TalkProgress.trackRemainingSeconds(track, positionMs)) ?: ""
                        track.durationSeconds > 0 -> formatDuration(track.durationSeconds)
                        else -> ""
                    }
                    Text(trailing, fontSize = 14.sp, color = FbaColors.meta)
                }
            }
        }
    }
}
