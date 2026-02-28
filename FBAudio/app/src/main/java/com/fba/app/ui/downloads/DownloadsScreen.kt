package com.fba.app.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fba.app.data.local.DownloadStatus
import com.fba.app.ui.components.EmptyState
import com.fba.app.ui.components.TalkCard
import com.fba.app.ui.components.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onTalkClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    val totalBytes = downloads
        .filter { it.status == DownloadStatus.COMPLETE }
        .sumOf { it.totalBytes }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Downloads")
                        if (totalBytes > 0) {
                            Text(
                                text = "Total: ${formatFileSize(totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyState(
                message = "No downloads yet",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(downloads, key = { it.catNum }) { download ->
                    val sizeText = if (download.status == DownloadStatus.COMPLETE && download.totalBytes > 0)
                        formatFileSize(download.totalBytes) else ""
                    TalkCard(
                        title = download.title,
                        speaker = download.speaker,
                        imageUrl = download.imageUrl,
                        subtitle = sizeText.ifBlank { null },
                        onClick = { onTalkClick(download.catNum) },
                        trailing = {
                            when (download.status) {
                                DownloadStatus.COMPLETE -> {
                                    IconButton(onClick = { viewModel.deleteDownload(download.catNum) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                                    CircularProgressIndicator(
                                        progress = download.progress / 100f,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                DownloadStatus.FAILED -> {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = "Failed",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
