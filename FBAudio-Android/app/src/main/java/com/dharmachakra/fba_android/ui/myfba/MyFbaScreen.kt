package com.dharmachakra.fba_android.ui.myfba

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.FeatureFlags
import com.dharmachakra.fba_android.ui.components.Artwork
import com.dharmachakra.fba_android.ui.components.CompactTalkRow
import com.dharmachakra.fba_android.ui.components.FbaPrimaryButton
import com.dharmachakra.fba_android.ui.components.SupportFbaButton
import com.dharmachakra.fba_android.ui.player.PlaybackMath
import com.dharmachakra.fba_android.ui.player.PlayerViewModel
import com.dharmachakra.fba_android.ui.theme.FbaColors

internal val PageMargin = 20.dp

/**
 * My FBA (v1.0 design, SPEC "My FBA"): the listener's photo top right (opens the Account
 * sheet), the sign-in card when signed out, Continue listening, Library (Downloads, Listening
 * history), Recently listened, Help (Settings, Contact support) and Support FBA. A new
 * listener sees a dashed empty Continue listening card that points to Explore.
 */
@Composable
fun MyFbaScreen(
    onTalkClick: (String) -> Unit,
    onDonateClick: () -> Unit,
    onLoginClick: () -> Unit,
    onJoinClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onContinueSeeAll: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onExploreClick: () -> Unit = {},
    onManageAccount: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    playerViewModel: PlayerViewModel? = null,
    viewModel: MyFbaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    val player = playerViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    var showAccount by remember { mutableStateOf(false) }

    // Pull the account's web history each time the tab is opened while signed in.
    LaunchedEffect(auth.loggedIn) { if (auth.loggedIn) viewModel.syncHistory() }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), containerColor = Color.White) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = PageMargin, end = PageMargin, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("My FBA", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = FbaColors.black, modifier = Modifier.weight(1f))
                    if (auth.loggedIn) Avatar(auth.avatarUrl, auth.username, 40) { showAccount = true }
                }
            }
            if (!auth.loggedIn && FeatureFlags.AUTH) item { SignInCard(onLoginClick, onCreateAccount) }

            // ---- Continue listening ----
            item {
                val cont = state.continueListening
                Section("Continue listening", onSeeAll = if (cont.isNotEmpty()) onContinueSeeAll else null) {
                    if (cont.isEmpty()) {
                        EmptyContinueCard(onExploreClick)
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = PageMargin), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(cont.take(10), key = { it.catNum }) { r ->
                                Column(Modifier.width(200.dp).clickable(role = Role.Button) { onTalkClick(r.catNum) }) {
                                    Artwork(r.imageUrl, aspect = 200f / 134f, modifier = Modifier.size(200.dp, 134.dp).clip(RoundedCornerShape(12.dp)))
                                    if (r.totalDurationSeconds > 0) {
                                        LinearProgressIndicator(
                                            progress = { MyFbaRules.progress(r) },
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp),
                                            color = MaterialTheme.colorScheme.primary, trackColor = FbaColors.divider,
                                            strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                                        )
                                    }
                                    Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = FbaColors.black, modifier = Modifier.padding(top = 6.dp))
                                    if (r.totalDurationSeconds > 0) {
                                        PlaybackMath.timeLeftLabel(MyFbaRules.remainingSeconds(r))?.let { Text(it, fontSize = 13.sp, color = FbaColors.meta) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Library ----
            item {
                Column(Modifier.padding(top = 20.dp)) {
                    SectionTitle("Library")
                    NavRow("Downloads", MyFbaRules.downloadsSummary(state.downloadCount, state.downloadBytes), onDownloadsClick)
                    if (!state.isNewListener) NavRow("Listening history", null, onHistoryClick)
                }
            }

            // ---- Recently listened (hidden until something has been played) ----
            if (state.recentlyListened.isNotEmpty()) item {
                Section("Recently listened", onSeeAll = onHistoryClick) {
                    Column(Modifier.padding(horizontal = PageMargin)) {
                        state.recentlyListened.take(3).forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(color = FbaColors.divider)
                            HistoryRow(r, onTalkClick, playerViewModel, player?.activeCatNum, player?.isPlaying == true)
                        }
                    }
                }
            }

            // ---- Help ----
            item {
                Column(Modifier.padding(top = 20.dp)) {
                    SectionTitle("Help")
                    NavRow("Settings", null, onSettingsClick)
                    NavRow("Contact support", null, onHelpClick)
                    if (FeatureFlags.MEMBERSHIP_GATING) NavRow("Join", null, onJoinClick)
                }
            }
            item { SupportFbaButton(onDonateClick, Modifier.padding(start = PageMargin, end = PageMargin, top = 24.dp)) }
        }
    }

    if (showAccount) {
        AccountSheet(
            avatarUrl = auth.avatarUrl, name = auth.username, email = auth.email, isOrderMember = auth.isOrderMember,
            onManageAccount = { showAccount = false; onManageAccount() },
            onSignOut = { showAccount = false; viewModel.logout() },
            onDismiss = { showAccount = false },
        )
    }
}

/** A recently listened talk as a compact row: "Speaker · Listened" once finished. */
@Composable
internal fun HistoryRow(
    r: com.dharmachakra.fba_android.data.local.RecentlyListenedEntity,
    onTalkClick: (String) -> Unit,
    playerViewModel: PlayerViewModel?,
    currentCatNum: String?,
    isPlaying: Boolean,
) {
    val finished = MyFbaRules.isFinished(r)
    CompactTalkRow(
        title = r.title,
        meta = listOfNotNull(r.speaker.ifBlank { null }, if (finished) "Listened" else null).joinToString(" · "),
        imageUrl = r.imageUrl,
        onClick = { onTalkClick(r.catNum) },
        onPlay = playerViewModel?.let { vm ->
            { if (currentCatNum == r.catNum) vm.togglePlayPause() else vm.playTalk(r.catNum) }
        },
        isCurrent = currentCatNum == r.catNum,
        isPlaying = isPlaying,
        listened = finished,
    )
}

/** Account sheet (tap your photo): photo, name, email, Order member, Manage account, Sign out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    avatarUrl: String,
    name: String,
    email: String,
    isOrderMember: Boolean,
    onManageAccount: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Avatar(avatarUrl, name, 72, onClick = null)
            Text(name.ifBlank { "Your account" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
            if (email.isNotBlank()) Text(email, fontSize = 15.sp, color = FbaColors.meta)
            if (isOrderMember) {
                // Always Order Only Blue, whatever the page colour.
                Text(
                    "Order member", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FbaColors.orderOnly.deep,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(FbaColors.orderOnly.tint)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = FbaColors.divider)
            Text("Manage account", fontSize = 17.sp, color = FbaColors.black,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onManageAccount).padding(vertical = 14.dp))
            HorizontalDivider(color = FbaColors.divider)
            Text("Sign out", fontSize = 17.sp, color = FbaColors.destructive,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSignOut).padding(vertical = 14.dp))
            HorizontalDivider(color = FbaColors.divider)
            Text("Signing out keeps your listening history and downloads on this phone.", fontSize = 13.sp,
                color = FbaColors.meta, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

/** The listener's photo in a circle, or their initial in a page-tint circle. */
@Composable
private fun Avatar(url: String, name: String, sizeDp: Int, onClick: (() -> Unit)?) {
    var modifier = Modifier.size(sizeDp.dp).clip(CircleShape)
    if (onClick != null) modifier = modifier.clickable(role = Role.Button, onClickLabel = "Account", onClick = onClick)
    if (url.isNotBlank()) {
        coil.compose.AsyncImage(model = url, contentDescription = "Account", modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", fontSize = (sizeDp * 0.42).sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Signed out: the sign-in card in the page tint (Dharmachakra's wording, 8 Oct). */
@Composable
private fun SignInCard(onSignIn: () -> Unit, onCreateAccount: () -> Unit) {
    Column(
        Modifier.padding(horizontal = PageMargin, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Keep your place on every device", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black)
        Text("Sign in to sync your FBA history and continue listening on any phone, tablet or at freebuddhistaudio.com.",
            fontSize = 15.sp, lineHeight = 21.sp, color = FbaColors.black)
        Text("Order members: sign in to hear Order talks and access Sangharakshita's seminar texts.",
            fontSize = 15.sp, lineHeight = 21.sp, color = FbaColors.black)
        Spacer(Modifier.height(4.dp))
        FbaPrimaryButton("Sign in", onSignIn, Modifier.fillMaxWidth())
        Text(
            "New to FBA? Create an account", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable(role = Role.Button, onClick = onCreateAccount).padding(6.dp),
        )
    }
}

/** New listener: a dashed empty card pointing to Explore. */
@Composable
private fun EmptyContinueCard(onExplore: () -> Unit) {
    val dash = FbaColors.grey500
    Column(
        Modifier.padding(horizontal = PageMargin).fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    dash, cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Nothing here yet. Talks you start will appear here, so you can pick up where you left off.",
            fontSize = 15.sp, lineHeight = 21.sp, color = FbaColors.meta)
        FbaPrimaryButton("Explore talks", onExplore)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black,
        modifier = Modifier.padding(horizontal = PageMargin).padding(bottom = 4.dp))
}

@Composable
private fun Section(title: String, onSeeAll: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = PageMargin), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                Text("See all", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onSeeAll).padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** A grouped-list row: title, optional value, chevron. */
@Composable
internal fun NavRow(title: String, value: String?, onClick: () -> Unit, titleColor: Color = FbaColors.black) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = PageMargin, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 17.sp, color = titleColor, modifier = Modifier.weight(1f))
            if (value != null) Text(value, fontSize = 15.sp, color = FbaColors.meta, modifier = Modifier.padding(end = 4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FbaColors.grey500)
        }
        HorizontalDivider(color = FbaColors.divider, modifier = Modifier.padding(start = PageMargin))
    }
}
