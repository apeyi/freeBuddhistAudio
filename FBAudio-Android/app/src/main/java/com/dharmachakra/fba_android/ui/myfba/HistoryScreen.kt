package com.dharmachakra.fba_android.ui.myfba

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.ui.components.CompactTalkRow
import com.dharmachakra.fba_android.ui.player.PlayerViewModel
import com.dharmachakra.fba_android.ui.theme.Brand
import com.dharmachakra.fba_android.ui.theme.FbaColors

/**
 * Continue listening (See all) and Listening history (SPEC "My FBA, Continue listening (See all)").
 * [continueOnly]: talks in progress with progress lines and Resume circles; swipe left marks a
 * talk finished. Otherwise every talk played, grouped by day.
 */
@Composable
fun HistoryScreen(
    continueOnly: Boolean,
    onTalkClick: (String) -> Unit,
    onBack: () -> Unit,
    onFullHistory: () -> Unit = {},
    playerViewModel: PlayerViewModel? = null,
    viewModel: MyFbaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player = playerViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    val current = player?.activeCatNum
    val isPlaying = player?.isPlaying == true
    val now = System.currentTimeMillis()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.White,
        topBar = {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FbaColors.black)
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = PageMargin).padding(bottom = 8.dp)) {
                    Text(if (continueOnly) "Continue listening" else "Listening history",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
                    val sub = if (continueOnly) {
                        if (state.continueListening.isEmpty()) "Nothing in progress" else MyFbaRules.inProgressLabel(state.continueListening.size)
                    } else if (state.recentlyListened.isEmpty()) "Talks you play will appear here." else null
                    if (sub != null) Text(sub, fontSize = 15.sp, color = FbaColors.meta, modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (continueOnly) {
                items(state.continueListening, key = { it.catNum }) { r ->
                    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                        if (v == SwipeToDismissBoxValue.EndToStart) { viewModel.markFinished(r.catNum); true } else false
                    })
                    SwipeToDismissBox(
                        state = swipe,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(Color(Brand.FBA_GREEN.set.deep)).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                    Text("Finished", color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        },
                    ) {
                        Column(Modifier.background(Color.White).padding(horizontal = PageMargin)) {
                            CompactTalkRow(
                                title = r.title,
                                meta = MyFbaRules.continueMeta(r),
                                imageUrl = r.imageUrl,
                                onClick = { onTalkClick(r.catNum) },
                                onPlay = playerViewModel?.let { vm ->
                                    { if (current == r.catNum) vm.togglePlayPause() else vm.playTalk(r.catNum) }
                                },
                                isCurrent = current == r.catNum,
                                isPlaying = isPlaying,
                            )
                            if (r.totalDurationSeconds > 0) {
                                LinearProgressIndicator(
                                    progress = { MyFbaRules.progress(r) },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(3.dp),
                                    color = MaterialTheme.colorScheme.primary, trackColor = FbaColors.divider,
                                    strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                                )
                            }
                            HorizontalDivider(color = FbaColors.divider)
                        }
                    }
                }
                item {
                    Text(
                        "Talks leave this list when you finish them, or after 90 days without a play. Swipe left on a talk to mark it as finished.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = FbaColors.meta,
                        modifier = Modifier.padding(horizontal = PageMargin, vertical = 14.dp),
                    )
                    Text(
                        "See full listening history", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onFullHistory).padding(horizontal = PageMargin, vertical = 8.dp),
                    )
                }
            } else {
                val groups = state.recentlyListened.sortedByDescending { it.listenedAt }
                    .groupBy { MyFbaRules.historyDayLabel(it.listenedAt, now) }
                groups.forEach { (day, entries) ->
                    item(key = "day-$day") {
                        Text(day, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FbaColors.meta,
                            modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 16.dp, bottom = 2.dp))
                    }
                    items(entries, key = { it.catNum }) { r ->
                        Column(Modifier.padding(horizontal = PageMargin)) {
                            HistoryRow(r, onTalkClick, playerViewModel, current, isPlaying)
                            HorizontalDivider(color = FbaColors.divider)
                        }
                    }
                }
            }
        }
    }
}
