package com.dharmachakra.fba_android.ui.list

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.ui.components.Artwork
import com.dharmachakra.fba_android.ui.components.CompactTalkRow
import com.dharmachakra.fba_android.ui.components.EmptyState
import com.dharmachakra.fba_android.ui.components.ErrorMessage
import com.dharmachakra.fba_android.ui.components.FbaPrimaryButton
import com.dharmachakra.fba_android.ui.components.LoadingIndicator
import com.dharmachakra.fba_android.ui.components.SupportFbaButton
import com.dharmachakra.fba_android.ui.components.ToolCircle
import com.dharmachakra.fba_android.ui.components.metaLine
import com.dharmachakra.fba_android.ui.player.PlaybackMath
import com.dharmachakra.fba_android.ui.player.PlayerViewModel
import com.dharmachakra.fba_android.ui.theme.FbaColors
import com.dharmachakra.fba_android.ui.theme.PageColorScope
import com.dharmachakra.fba_android.ui.theme.PageTheme

private val PageMargin = 20.dp

/**
 * A list of talks, series or index entries (v1.0 design): a grey label (PLACE, YEAR, SPEAKER,
 * COLLECTION), the title and talk count, then compact rows whose grey play circle plays the talk
 * in the Now Playing bar. Series get their own page, built like a talk page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    onItemClick: (SearchResult) -> Unit,
    onDonateClick: () -> Unit,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    onSpeakerClick: (String) -> Unit = {},
    onYearClick: (Int) -> Unit = {},
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player = playerViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    val nowPlaying = player?.currentTalk?.catNum
    val listState = rememberLazyListState()
    // Series: the title moves into the bar once the page title has scrolled away.
    val titleInBar by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var showMore by remember { mutableStateOf(false) }

    val playRow: (SearchResult) -> Unit = { item ->
        if (nowPlaying == item.catNum) playerViewModel?.togglePlayPause() else playerViewModel?.playTalk(item.catNum)
    }

    PageColorScope(if (state.isSeries && state.hasRemaster) PageTheme.REMASTERED else PageTheme.HOURLY) {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).padding(start = 12.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FbaColors.black)
                        }
                        if (state.isSeries && titleInBar) {
                            Text(state.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            TextButton(onClick = { state.items.firstOrNull()?.let(playRow) }) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Text("Play all", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (state.isSeries && titleInBar) HorizontalDivider(color = FbaColors.divider)
                }
            },
        ) { padding ->
            when {
                state.isLoading -> LoadingIndicator(Modifier.padding(padding))
                state.error != null -> ErrorMessage(state.error!!, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = PageMargin, end = PageMargin, bottom = 24.dp),
                ) {
                    if (state.isSeries) {
                        item { SeriesHeader(state, playRow, onSpeakerClick, onYearClick, onDonateClick, { viewModel.downloadAll() }, { showMore = true }) }
                    } else {
                        item { ListHeader(state) }
                    }
                    if (state.items.isEmpty()) {
                        item { EmptyState("No talks found", Modifier.height(200.dp)) }
                    }
                    itemsIndexed(state.items, key = { _, it -> "${it.path}|${it.catNum}" }) { index, item ->
                        if (index >= state.items.size - 6) {
                            LaunchedEffect(state.items.size) { viewModel.loadMore() }
                        }
                        if (index > 0) HorizontalDivider(color = FbaColors.divider)
                        val isTalk = !item.isSeries && !item.isCollection && !item.isBrowseLink
                        if (state.isSeries) {
                            SeriesRow(index, item, state, isCurrent = nowPlaying == item.catNum,
                                isPlaying = player?.isPlaying == true, onClick = { onItemClick(item) }, onPlay = { playRow(item) })
                        } else {
                            CompactTalkRow(
                                title = item.title.replaceFirstChar { it.uppercase() },
                                meta = item.metaLine(),
                                imageUrl = item.imageUrl,
                                onClick = { onItemClick(item) },
                                onPlay = if (isTalk && playerViewModel != null) ({ playRow(item) }) else null,
                                isCurrent = isTalk && nowPlaying == item.catNum,
                                isPlaying = player?.isPlaying == true,
                            )
                        }
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                    item { SupportFbaButton(onDonateClick, Modifier.padding(top = 20.dp)) }
                }
            }
        }

        if (showMore) {
            val context = LocalContext.current
            val clipboard = LocalClipboardManager.current
            ModalBottomSheet(onDismissRequest = { showMore = false }) {
                Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                    ListItem(headlineContent = { Text("Copy link") }, modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(seriesUrl(state.catNum))); showMore = false
                    })
                    if (state.speaker.isNotBlank()) {
                        ListItem(headlineContent = { Text("Go to speaker") }, modifier = Modifier.clickable {
                            showMore = false; onSpeakerClick(state.speaker)
                        })
                    }
                }
            }
        }
    }
}

private fun seriesUrl(catNum: String) = "https://www.freebuddhistaudio.com/series/details?num=${Uri.encode(catNum)}"

/** PLACE / YEAR / SPEAKER label, the title (26 Bold) and the number of talks. */
@Composable
private fun ListHeader(state: ListUiState) {
    Column(Modifier.padding(top = 4.dp, bottom = 10.dp)) {
        if (state.label.isNotBlank()) {
            Text(state.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FbaColors.meta, letterSpacing = 0.8.sp)
        }
        Text(state.title.replaceFirstChar { it.uppercase() }, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
        if (state.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(state.description, fontSize = 15.sp, lineHeight = 22.sp, color = FbaColors.black, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (state.totalItems > 0) {
            Spacer(Modifier.height(4.dp))
            Text("${state.totalItems} ${if (state.totalItems == 1) "talk" else "talks"}", fontSize = 14.sp, color = FbaColors.meta)
        }
    }
}

/**
 * Series page header (SPEC "Series page"): artwork, SERIES label, title, speaker, "7 talks ·
 * 1967", Play all or Resume Talk n (with "Play from the start"), tools, description.
 */
@Composable
private fun SeriesHeader(
    state: ListUiState,
    play: (SearchResult) -> Unit,
    onSpeakerClick: (String) -> Unit,
    onYearClick: (Int) -> Unit,
    onDonateClick: () -> Unit,
    onDownloadAll: () -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val resume = state.seriesResume
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Artwork(
            url = state.bannerUrl.ifBlank { state.imageUrl },
            aspect = 362f / 240f,
            modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SERIES", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FbaColors.meta, letterSpacing = 0.8.sp)
            Text(state.title, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
            if (state.speaker.isNotBlank()) {
                Text(state.speaker, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = FbaColors.nearBlack,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(role = Role.Button) { onSpeakerClick(state.speaker) })
            }
            Row {
                Text("${state.items.size} talks", fontSize = 14.sp, color = FbaColors.meta)
                if (state.year > 0) {
                    Text(" · ", fontSize = 14.sp, color = FbaColors.meta)
                    Text(state.year.toString(), fontSize = 14.sp, color = FbaColors.meta, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(role = Role.Button) { onYearClick(state.year) })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (resume != null) {
                FbaPrimaryButton("Resume Talk ${resume.index + 1}", { play(state.items[resume.index]) },
                    icon = Icons.Default.PlayArrow, shape = RoundedCornerShape(12.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val left = resume.remainingSeconds?.let { PlaybackMath.timeLeftLabel(it) }
                    Text(listOfNotNull(state.items[resume.index].title, left).joinToString(" · "),
                        fontSize = 14.sp, color = FbaColors.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Play from the start", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FbaColors.nearBlack,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.padding(top = 2.dp).clickable(role = Role.Button) { state.items.firstOrNull()?.let(play) })
                }
            } else {
                FbaPrimaryButton("Play all", { state.items.firstOrNull()?.let(play) },
                    icon = Icons.Default.PlayArrow, shape = RoundedCornerShape(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolCircle("Download", onClick = onDownloadAll, icon = Icons.Outlined.FileDownload, contentDescription = "Download all talks")
                ToolCircle("Donate", onClick = onDonateClick, icon = Icons.Default.Favorite, iconTint = MaterialTheme.colorScheme.primary)
                ToolCircle("Share", onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${state.title}\n${seriesUrl(state.catNum)}")
                    }
                    context.startActivity(Intent.createChooser(send, "Share series"))
                }, icon = Icons.Default.Share)
                ToolCircle("More", onClick = onMore, icon = Icons.Default.MoreVert, contentDescription = "More options")
            }
        }
        if (state.description.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            var overflows by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.description, fontSize = 16.sp, lineHeight = 24.sp, color = FbaColors.black,
                    maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow })
                if (overflows || expanded) {
                    Text(if (expanded) "Show less" else "Read more", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { expanded = !expanded })
                }
            }
        }
        Text("Talks (${state.items.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
    }
}

/** Series row: 84 x 56 image, title, "Talk 1" (speaker added when the series has several). */
@Composable
private fun SeriesRow(index: Int, item: SearchResult, state: ListUiState, isCurrent: Boolean, isPlaying: Boolean,
                      onClick: () -> Unit, onPlay: () -> Unit) {
    val saved = state.saved[item.catNum]
    val progress = SeriesProgress.state(saved)
    val oneSpeaker = state.items.map { it.speaker }.distinct().size <= 1
    val parts = buildList {
        add("Talk ${index + 1}")
        if (!oneSpeaker && item.speaker.isNotBlank()) add(item.speaker)
        when (progress) {
            SeriesProgress.State.LISTENED -> add("Listened")
            SeriesProgress.State.IN_PROGRESS -> saved?.let { s ->
                PlaybackMath.timeLeftLabel((s.totalSeconds - (s.positionMs / 1000).toInt()).coerceAtLeast(0))?.let(::add)
            }
            else -> Unit
        }
    }
    CompactTalkRow(
        title = item.title,
        meta = parts.joinToString(" · "),
        imageUrl = item.imageUrl,
        onClick = onClick,
        onPlay = onPlay,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        listened = progress == SeriesProgress.State.LISTENED,
        thumbWidth = 84.dp,
        thumbHeight = 56.dp,
    )
}
