package com.dharmachakra.fba_android.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.domain.model.Talk
import com.dharmachakra.fba_android.ui.components.TalkArtwork
import com.dharmachakra.fba_android.ui.theme.FbaColors
import com.dharmachakra.fba_android.ui.theme.FbaDimens

/**
 * Now Playing bar (Figma MiniPlayer): sits above the navigation bar once something has
 * been played. Artwork, one-line title, speaker (or "Continue listening · 18 min left"
 * for the talk restored at launch), play/pause. Tap opens the full-screen player;
 * swiping it away while paused clears it until something is played again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onExpand: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = state.isVisible && state.currentTalk != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val talk = state.currentTalk ?: return@AnimatedVisibility
        val subtitle = state.continueListeningLabel ?: talk.speaker
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled && !state.isPlaying) {
                    viewModel.dismissNowPlaying(); true
                } else false
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = !state.isPlaying,
            enableDismissFromEndToStart = !state.isPlaying,
            backgroundContent = {},
            modifier = Modifier.padding(start = FbaDimens.margin, end = FbaDimens.margin, top = 8.dp, bottom = 8.dp),
        ) {
            NowPlayingBar(
                talk = talk,
                subtitle = subtitle,
                isPlaying = state.isPlaying,
                onExpand = onExpand,
                onTogglePlay = { viewModel.togglePlayPause() },
                onDismiss = if (state.isPlaying) null else ({ viewModel.dismissNowPlaying() }),
            )
        }
    }
}

@Composable
private fun NowPlayingBar(
    talk: Talk,
    subtitle: String,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(FbaColors.grey50)
            .border(1.dp, Color.Black.copy(alpha = 0.06f), shape)
            .clickable(onClickLabel = "Open player", onClick = onExpand)
            .semantics {
                if (onDismiss != null) {
                    customActions = listOf(CustomAccessibilityAction("Clear") { onDismiss(); true })
                }
            }
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TalkArtwork(
            talk = talk,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(FbaDimens.radiusThumb)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = talk.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = FbaColors.black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = FbaColors.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(FbaDimens.minTouch), contentAlignment = Alignment.Center) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = FbaColors.black,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
