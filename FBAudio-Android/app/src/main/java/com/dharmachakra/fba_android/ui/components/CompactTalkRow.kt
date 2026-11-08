package com.dharmachakra.fba_android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.ui.theme.FbaColors

/** Black play/pause symbol in a light grey circle (lists). */
private val CircleGrey = Color(0xFFF0F0F0)
private val SymbolBlack = Color(0xFF141414)

/**
 * The design's compact row (Figma TalkRow): a 56 rounded thumbnail (talk artwork rule), the
 * title on up to two lines and a grey "Speaker · year" line. Talk rows end in a black play
 * symbol in a light grey circle: the circle plays in the Now Playing bar, the rest of the row
 * opens the page. The playing row shows a pause symbol and its title and "Playing" line in the
 * page colour. [onPlay] null = no circle (series, speakers, places).
 */
@Composable
fun CompactTalkRow(
    title: String,
    meta: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    listened: Boolean = false,
    thumbWidth: Dp = 56.dp,
    thumbHeight: Dp = 56.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            Artwork(
                url = imageUrl,
                aspect = thumbWidth.value / thumbHeight.value,
                modifier = Modifier.size(thumbWidth, thumbHeight).clip(RoundedCornerShape(8.dp)),
            )
            if (listened) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, "Listened", tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) accent else FbaColors.black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val line = if (isCurrent) listOf("Playing", meta).filter { it.isNotBlank() }.joinToString(" · ") else meta
            if (line.isNotBlank()) {
                Text(line, fontSize = 13.sp, color = if (isCurrent) accent else FbaColors.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onPlay != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CircleGrey)
                    .clickable(role = Role.Button, onClickLabel = if (isCurrent && isPlaying) "Pause" else "Play", onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isCurrent && isPlaying) "Pause $title" else "Play $title",
                    tint = SymbolBlack,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** The grey line under a list item's title: "Speaker · year", or "Series · Speaker". */
fun SearchResult.metaLine(): String = when {
    isSeries -> listOf("Series", speaker, year.takeIf { it > 0 }?.toString()).filter { !it.isNullOrBlank() }.joinToString(" · ")
    isCollection -> "Collection"
    isBrowseLink -> ""
    else -> listOf(speaker, year.takeIf { it > 0 }?.toString()).filter { !it.isNullOrBlank() }.joinToString(" · ")
}
