package com.fba.app.ui.myfba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fba.app.FeatureFlags
import com.fba.app.ui.components.TalkCard
import com.fba.app.ui.components.formatDuration
import com.fba.app.ui.components.safeFraction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFbaScreen(
    onTalkClick: (String) -> Unit,
    onDonateClick: () -> Unit,
    onLoginClick: () -> Unit,
    onJoinClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: MyFbaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val auth by viewModel.authState.collectAsStateWithLifecycle()

    // Pull the account's web history each time the tab is opened while logged in.
    LaunchedEffect(auth.loggedIn) { if (auth.loggedIn) viewModel.syncHistory() }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("My FBA") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // --- Account ---
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (auth.loggedIn && auth.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = auth.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            when {
                                auth.loggedIn -> {
                                    Text(auth.username.ifBlank { "Logged in" }, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (auth.isOrderMember) "Order member" else "FBA account",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FeatureFlags.AUTH -> {
                                    Text("Not logged in", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Log in with your FBA account to sync your listening history.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                else -> {
                                    Text("Your FBA", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Account login arrives with the new FBA service.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (FeatureFlags.AUTH) {
                            Spacer(Modifier.width(12.dp))
                            if (auth.loggedIn) {
                                OutlinedButton(onClick = { viewModel.logout() }) { Text("Log out") }
                            } else {
                                Button(onClick = onLoginClick) { Text("Log in") }
                            }
                        }
                    }
                }
            }

            // --- Donate ---
            item {
                Button(
                    onClick = onDonateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text("Donate") }
                if (FeatureFlags.MEMBERSHIP_GATING) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onJoinClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) { Text("Join") }
                }
            }

            // --- Recently listened ---
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Recently Listened",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (state.recentlyListened.isEmpty()) {
                    Text(
                        "Talks you play will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.recentlyListened, key = { it.catNum }) { entry ->
                val totalMs = entry.totalDurationSeconds * 1000L
                val progress = if (totalMs > 0) (entry.positionMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
                val isCompleted = progress > 0.95f
                val subtitle = when {
                    isCompleted -> "Completed · ${formatDuration(entry.totalDurationSeconds)}"
                    entry.totalDurationSeconds > 0 ->
                        "${formatDuration((entry.positionMs / 1000).toInt())} / ${formatDuration(entry.totalDurationSeconds)}"
                    else -> null
                }
                val isDownloaded = entry.catNum in state.downloadedCatNums

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TalkCard(
                        title = entry.title,
                        speaker = entry.speaker,
                        imageUrl = entry.imageUrl,
                        subtitle = subtitle,
                        onClick = { onTalkClick(entry.catNum) },
                        trailing = if (isCompleted || isDownloaded) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCompleted) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Completed",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    if (isDownloaded) {
                                        if (isCompleted) Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        } else null,
                    )
                    if (progress > 0f && !isCompleted) {
                        LinearProgressIndicator(
                            progress = { progress.safeFraction() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .height(3.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Butt,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
            }

        }
    }
}
