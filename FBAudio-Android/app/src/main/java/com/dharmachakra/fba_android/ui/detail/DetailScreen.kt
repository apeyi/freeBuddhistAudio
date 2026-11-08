package com.dharmachakra.fba_android.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.BuildConfig
import com.dharmachakra.fba_android.R
import com.dharmachakra.fba_android.data.local.DownloadStatus
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.domain.model.Talk
import com.dharmachakra.fba_android.ui.components.Artwork
import com.dharmachakra.fba_android.ui.components.ErrorMessage
import com.dharmachakra.fba_android.ui.components.FbaPrimaryButton
import com.dharmachakra.fba_android.ui.components.FbaTextTabs
import com.dharmachakra.fba_android.ui.components.LoadingIndicator
import com.dharmachakra.fba_android.ui.components.ReportProblem
import com.dharmachakra.fba_android.ui.components.SupportFbaButton
import com.dharmachakra.fba_android.ui.components.TalkArtwork
import com.dharmachakra.fba_android.ui.components.ToolCircle
import com.dharmachakra.fba_android.ui.components.formatDuration
import com.dharmachakra.fba_android.ui.player.PlaybackMath
import com.dharmachakra.fba_android.ui.player.PlayerViewModel
import com.dharmachakra.fba_android.ui.theme.FbaColors
import com.dharmachakra.fba_android.ui.theme.FbaDimens
import com.dharmachakra.fba_android.ui.theme.PageColorScope
import com.dharmachakra.fba_android.ui.theme.PageTheme

/** Page margin on the Android talk page frame. */
private val PageMargin = 20.dp

/**
 * Talk page (v1.0 design, Android Candidate Release): artwork, title block with speaker /
 * length and year / place / series links, the version switch on remastered talks, one primary
 * action (Play or Resume), the grey tool row, description, Tracks | Related talks (or
 * Description | Related talks for single-track talks) and Support FBA. Remastered talks are
 * gold everywhere on the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    catNum: String,
    onPlay: (String) -> Unit,
    onPlayChapter: (String, Int) -> Unit = { c, _ -> onPlay(c) },
    onPlayFromStart: (String) -> Unit = onPlay,
    onBack: () -> Unit,
    onSpeakerClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    onPlaceClick: (slug: String, name: String) -> Unit = { _, _ -> },
    onYearClick: (Int) -> Unit = {},
    onTalkClick: (String) -> Unit = {},
    onDigitalLegacyClick: () -> Unit = {},
    onTranscriptClick: (String) -> Unit = {},
    onDonateClick: () -> Unit = {},
    onJoinClick: () -> Unit = {},
    /** False when downloads are member-only and the user isn't a member. */
    canDownload: Boolean = true,
    playerViewModel: PlayerViewModel,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            // Android top bar: back arrow only (the title sits in the content).
            Box(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 10.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FbaColors.black)
                }
            }
        },
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
                val isActive = playerState.currentTalk?.catNum == catNum
                val isPlaying = isActive && playerState.isPlaying
                // Live position while this talk is in the player, else the saved point.
                val (savedTrack, savedPos) = if (isActive) playerState.currentTrackIndex to playerState.currentPosition
                    else viewModel.savedPoint(talk)
                val total = PlaybackMath.totalDurationSeconds(talk.durationSeconds, talk.tracks, 0)
                val resume = TalkProgress.resume(talk.tracks, total, savedTrack, savedPos)

                PageColorScope(if (talk.hasRemaster) PageTheme.REMASTERED else PageTheme.HOURLY) {
                    TalkPageContent(
                        talk = talk,
                        state = state,
                        resume = resume,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        currentTrack = if (isActive) playerState.currentTrackIndex else -1,
                        canDownload = canDownload,
                        modifier = Modifier.padding(padding),
                        onPrimary = {
                            when {
                                isActive -> playerViewModel.togglePlayPause()
                                else -> onPlay(catNum)
                            }
                        },
                        onPlayFromStart = { onPlayFromStart(catNum) },
                        onTrackClick = { index ->
                            if (isActive) playerViewModel.playTrackByIndex(index)
                            // Load the talk starting at the tapped track; calling playTrackByIndex
                            // here would race the async load and play the wrong talk.
                            else onPlayChapter(catNum, index)
                        },
                        onSpeakerClick = onSpeakerClick,
                        onSeriesClick = onSeriesClick,
                        onPlaceClick = onPlaceClick,
                        onYearClick = onYearClick,
                        onTalkClick = onTalkClick,
                        onDigitalLegacyClick = onDigitalLegacyClick,
                        onTranscriptClick = onTranscriptClick,
                        onDonateClick = onDonateClick,
                        onVersionChange = { enhanced ->
                            viewModel.setUseRemaster(enhanced)
                            if (isActive) playerViewModel.setUseRemaster(enhanced)
                        },
                        onDownload = {
                            val download = state.download?.takeUnless { it.isTranscriptOnly }
                            when (download?.status) {
                                DownloadStatus.COMPLETE -> showDeleteConfirm = true
                                DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> viewModel.cancelDownload()
                                else -> if (canDownload) viewModel.startDownload() else onJoinClick()
                            }
                        },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, talk.title)
                                putExtra(Intent.EXTRA_TEXT, "${talk.title} by ${talk.speaker}\n${talkUrl(talk.catNum)}")
                            }
                            context.startActivity(Intent.createChooser(send, "Share talk"))
                        },
                        onMore = { showMore = true },
                    )
                }

                if (showMore) {
                    MoreSheet(
                        talk = talk,
                        transcriptSaved = state.download?.isTranscriptOnly == true ||
                            state.download?.status == DownloadStatus.COMPLETE,
                        onDismiss = { showMore = false },
                        onDownloadTranscript = {
                            if (canDownload) viewModel.startTranscriptDownload() else onJoinClick()
                        },
                        onCopyLink = { clipboard.setText(AnnotatedString(talkUrl(talk.catNum))) },
                        onSeries = { onSeriesClick(talk.seriesHref.ifBlank { talk.series }) },
                        onSpeaker = { onSpeakerClick(talk.speaker) },
                        onReport = { showReport = true },
                    )
                }
                if (showReport) {
                    ReportSheet(
                        talk = talk,
                        onDismiss = { showReport = false },
                        onReason = { reason ->
                            val position = if (isActive) {
                                (PlaybackMath.cumulativePositionMs(talk.tracks, playerState.currentTrackIndex, playerState.currentPosition) / 1000).toInt()
                            } else null
                            val email = ReportProblem.email(
                                reason = reason,
                                talkTitle = talk.title,
                                catNum = talk.catNum,
                                positionSeconds = position,
                                device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
                                appVersion = BuildConfig.VERSION_NAME,
                                signedIn = false,
                            )
                            val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(email.to))
                                putExtra(Intent.EXTRA_SUBJECT, email.subject)
                                putExtra(Intent.EXTRA_TEXT, email.body)
                            }
                            runCatching { context.startActivity(mail) }
                                .onFailure { clipboard.setText(AnnotatedString(email.to)) }
                            showReport = false
                        },
                    )
                }
            }
        }
    }

    // Confirm before deleting the offline files (matches the Downloads screen)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove download?") },
            text = { Text("This removes the offline files from your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDownload()
                    showDeleteConfirm = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

private fun talkUrl(catNum: String) = "https://www.freebuddhistaudio.com/audio/details?num=${Uri.encode(catNum)}"

@Composable
private fun TalkPageContent(
    talk: Talk,
    state: DetailUiState,
    resume: TalkProgress.Resume?,
    isActive: Boolean,
    isPlaying: Boolean,
    currentTrack: Int,
    canDownload: Boolean,
    modifier: Modifier,
    onPrimary: () -> Unit,
    onPlayFromStart: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onSpeakerClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onPlaceClick: (String, String) -> Unit,
    onYearClick: (Int) -> Unit,
    onTalkClick: (String) -> Unit,
    onDigitalLegacyClick: () -> Unit,
    onTranscriptClick: (String) -> Unit,
    onDonateClick: () -> Unit,
    onVersionChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val multiTrack = talk.tracks.size > 1
    val description = remember(talk.description) { plainDescription(talk.description) }
    var tab by rememberSaveable(talk.catNum) { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageMargin)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TalkArtwork(
            talk = talk,
            aspect = 362f / 240f,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(FbaDimens.radiusArtwork)),
        )

        // ---- Title block ----
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(talk.title, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
            if (talk.speaker.isNotBlank()) {
                Text(
                    talk.speaker,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = FbaColors.nearBlack,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "All talks by ${talk.speaker}") { onSpeakerClick(talk.speaker) },
                )
            }
            LengthAndYear(talk, onYearClick)
            if (talk.hasRemaster) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(role = Role.Button, onClick = onDigitalLegacyClick),
                ) {
                    Image(painterResource(R.drawable.dharmachakra_gold), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "digitally remastered",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = FbaColors.nearBlack,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
            if (talk.placeName.isNotBlank() || talk.series.isNotBlank()) {
                Column(Modifier.padding(top = 2.dp)) {
                    if (talk.placeName.isNotBlank()) {
                        Text(
                            talk.placeName,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            color = FbaColors.meta,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "All talks from ${talk.placeName}") {
                                onPlaceClick(talk.placeSlug.ifBlank { talk.placeName }, talk.placeName)
                            },
                        )
                    }
                    if (talk.series.isNotBlank()) {
                        val link = talk.seriesHref.ifBlank { talk.series }
                        Text(
                            buildAnnotatedString {
                                append("Part of the series ")
                                withLink(LinkAnnotation.Clickable("series", TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))) { onSeriesClick(link) }) {
                                    append(talk.series)
                                }
                            },
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            color = FbaColors.meta,
                        )
                    }
                }
            }
        }

        if (talk.hasRemaster) {
            VersionSwitch(
                enhanced = state.useRemaster,
                enabled = state.download?.status != DownloadStatus.COMPLETE,
                onChange = onVersionChange,
            )
        }

        // ---- Primary action + tools ----
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val label = when {
                isPlaying -> "Pause"
                resume != null -> "Resume"
                else -> "Play"
            }
            FbaPrimaryButton(
                text = label,
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                onClick = onPrimary,
                shape = RoundedCornerShape(12.dp),
            )
            if (resume != null && !isPlaying) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TalkProgress.resumeLabel(resume, talk.tracks.size), fontSize = 14.sp, color = FbaColors.meta)
                    Text(
                        "Play from the start",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = FbaColors.nearBlack,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable(role = Role.Button, onClick = onPlayFromStart),
                    )
                }
            }
            ToolRow(talk, state, canDownload, onDownload, onTranscriptClick, onDonateClick, onShare, onMore)
        }

        // ---- Description (multi-track: up to four lines, Read more) ----
        if (multiTrack && description.isNotBlank()) {
            ExpandableDescription(description)
        }

        // ---- Tabs ----
        val tabs = if (multiTrack) listOf("Tracks (${talk.tracks.size})", "Related talks")
                   else listOf("Description", "Related talks")
        FbaTextTabs(tabs, tab, { tab = it })
        when {
            tab == 1 -> RelatedTalks(state.related, onTalkClick)
            multiTrack -> TrackList(talk, resume, currentTrack, onTrackClick)
            else -> Text(
                description.ifBlank { "No description for this talk." },
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = FbaColors.black,
            )
        }

        SupportFbaButton(onDonateClick, Modifier.padding(top = 4.dp))
    }
}

/** "90 min · 2026" in grey, the year underlined as a link to that year's talks. */
@Composable
private fun LengthAndYear(talk: Talk, onYearClick: (Int) -> Unit) {
    val total = PlaybackMath.totalDurationSeconds(talk.durationSeconds, talk.tracks, 0)
    val length = if (total > 0) lengthLabel(total) else null
    if (length == null && talk.year <= 0) return
    Text(
        buildAnnotatedString {
            if (length != null) append(length)
            if (length != null && talk.year > 0) append(" · ")
            if (talk.year > 0) {
                withLink(LinkAnnotation.Clickable("year", TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))) { onYearClick(talk.year) }) {
                    append(talk.year.toString())
                }
            }
        },
        fontSize = 14.sp,
        color = FbaColors.meta,
    )
}

/** Talk length as the design writes it: "59 min", "90 min", then "2 hr 5 min", "8 hr 14 min". */
fun lengthLabel(seconds: Int): String {
    val minutes = (seconds + 30) / 60
    if (minutes < 120) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h hr" else "$h hr $m min"
}

/** Original | switch | Enhanced (remastered talks only); the chosen side in gold-deep. */
@Composable
private fun VersionSwitch(enhanced: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val gold = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Original",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = if (!enhanced) gold else FbaColors.grey500,
            modifier = Modifier.clickable(enabled = enabled && enhanced) { onChange(false) },
        )
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = enhanced,
            onCheckedChange = onChange,
            enabled = enabled,
            thumbContent = if (enhanced) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = FbaColors.gold.base,
                checkedThumbColor = FbaColors.white,
                checkedIconColor = FbaColors.gold.deep,
            ),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            "Enhanced",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = if (enhanced) gold else FbaColors.grey500,
            modifier = Modifier.clickable(enabled = enabled && !enhanced) { onChange(true) },
        )
    }
}

@Composable
private fun ToolRow(
    talk: Talk,
    state: DetailUiState,
    canDownload: Boolean,
    onDownload: () -> Unit,
    onTranscriptClick: (String) -> Unit,
    onDonateClick: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val download = state.download?.takeUnless { it.isTranscriptOnly }
        when (download?.status) {
            DownloadStatus.COMPLETE -> ToolCircle(
                label = "Downloaded",
                icon = Icons.Default.DownloadDone,
                iconTint = accent,
                contentDescription = "Downloaded. Remove download",
                onClick = onDownload,
            )
            DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> ToolCircle(
                label = "${download.progress}%",
                contentDescription = "Downloading, ${download.progress} percent. Stop download",
                onClick = onDownload,
            ) {
                CircularProgressIndicator(
                    progress = { download.progress / 100f },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = accent,
                    trackColor = FbaColors.divider,
                )
            }
            else -> ToolCircle(
                label = "Download",
                icon = Icons.Outlined.FileDownload,
                contentDescription = if (canDownload) "Download" else "Download (for members)",
                onClick = onDownload,
            )
        }
        if (talk.transcriptUrl.isNotBlank()) {
            ToolCircle("Transcript", onClick = { onTranscriptClick(talk.transcriptUrl) }, icon = Icons.Outlined.Description)
        }
        ToolCircle("Donate", onClick = onDonateClick, icon = Icons.Default.Favorite, iconTint = accent)
        ToolCircle("Share", onClick = onShare, icon = Icons.Default.Share)
        ToolCircle("More", onClick = onMore, icon = Icons.Default.MoreVert, contentDescription = "More options")
    }
}

@Composable
private fun ExpandableDescription(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = FbaColors.black,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Show less" else "Read more",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded },
            )
        }
    }
}

/**
 * Track rows: number (or a tick when listened), title, length (or "Listened" / time left).
 * The track in progress has its title in the page colour with a thin progress bar.
 */
@Composable
private fun TrackList(talk: Talk, resume: TalkProgress.Resume?, currentTrack: Int, onTrackClick: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column {
        talk.tracks.forEachIndexed { index, track ->
            if (index > 0) HorizontalDivider(color = FbaColors.divider)
            val stateOf = TalkProgress.trackState(index, resume)
            val highlighted = index == currentTrack || stateOf == TalkProgress.TrackState.IN_PROGRESS
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Play") { onTrackClick(index) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (stateOf == TalkProgress.TrackState.LISTENED) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = "Listened", tint = FbaColors.white, modifier = Modifier.size(12.dp))
                    }
                } else {
                    Text("${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FbaColors.grey500)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title.ifBlank { "Track ${index + 1}" },
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
                        color = if (highlighted) accent else FbaColors.black,
                    )
                    if (stateOf == TalkProgress.TrackState.IN_PROGRESS && resume != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { TalkProgress.trackFraction(track, resume.positionMs) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = accent,
                            trackColor = FbaColors.divider,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
                val trailing = when (stateOf) {
                    TalkProgress.TrackState.LISTENED -> "Listened"
                    TalkProgress.TrackState.IN_PROGRESS ->
                        resume?.let { PlaybackMath.timeLeftLabel(TalkProgress.trackRemainingSeconds(track, it.positionMs)) }
                            ?: formatDuration(track.durationSeconds)
                    else -> if (track.durationSeconds > 0) formatDuration(track.durationSeconds) else ""
                }
                Text(trailing, fontSize = 14.sp, color = FbaColors.meta)
            }
        }
    }
}

/** Related talks: 84 x 56 rounded image, title, "Speaker · year". */
@Composable
private fun RelatedTalks(related: List<SearchResult>?, onTalkClick: (String) -> Unit) {
    when {
        related == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        related.isEmpty() -> Text("No related talks yet.", fontSize = 15.sp, color = FbaColors.meta)
        else -> Column {
            related.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = FbaColors.divider)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onTalkClick(item.catNum) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        url = item.imageUrl,
                        aspect = 84f / 56f,
                        modifier = Modifier.width(84.dp).height(56.dp).clip(RoundedCornerShape(FbaDimens.radiusThumb)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FbaColors.black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(item.speaker.takeIf { it.isNotBlank() }, item.year.takeIf { it > 0 }?.toString())
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 13.sp, color = FbaColors.meta)
                    }
                }
            }
        }
    }
}

/** More: the less common options (SPEC talk page icon row). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    talk: Talk,
    transcriptSaved: Boolean,
    onDismiss: () -> Unit,
    onDownloadTranscript: () -> Unit,
    onCopyLink: () -> Unit,
    onSeries: () -> Unit,
    onSpeaker: () -> Unit,
    onReport: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(talk.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            fun row(label: String, action: () -> Unit): @Composable () -> Unit = {
                ListItem(
                    headlineContent = { Text(label) },
                    modifier = Modifier.clickable(role = Role.Button) { onDismiss(); action() },
                )
            }
            if (talk.transcriptUrl.isNotBlank() && !transcriptSaved) row("Download transcript", onDownloadTranscript)()
            row("Copy link", onCopyLink)()
            if (talk.series.isNotBlank()) row("Go to series", onSeries)()
            if (talk.speaker.isNotBlank()) row("Go to speaker", onSpeaker)()
            row("Report a problem", onReport)()
        }
    }
}

/** Report a problem: the talk, then the reasons; each opens an email to support. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(talk: Talk, onDismiss: () -> Unit, onReason: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text("Report a problem", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            Text("${talk.title} · ${talk.speaker}", fontSize = 14.sp, color = FbaColors.meta,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            ReportProblem.reasons.forEach { reason ->
                ListItem(
                    headlineContent = { Text(reason) },
                    modifier = Modifier.clickable(role = Role.Button) { onReason(reason) },
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}

/** Description text without HTML (the scraper sometimes leaves markup in). */
private fun plainDescription(raw: String): String {
    if (raw.isBlank()) return ""
    if (!raw.contains('<')) return raw.trim()
    val doc = org.jsoup.Jsoup.parse(raw)
    doc.select("p").prepend("\n\n")
    doc.select("br").append("\n")
    return doc.text().trim()
}
