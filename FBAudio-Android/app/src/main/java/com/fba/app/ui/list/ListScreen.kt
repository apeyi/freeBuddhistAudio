package com.fba.app.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fba.app.domain.model.SearchResult
import com.fba.app.ui.components.EmptyState
import com.fba.app.ui.components.ErrorMessage
import com.fba.app.ui.components.LoadingIndicator
import com.fba.app.ui.components.RemasterBadge
import com.fba.app.ui.components.TalkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    onItemClick: (SearchResult) -> Unit,
    onDonateClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.title.replaceFirstChar { it.uppercase() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))
            state.error != null -> ErrorMessage(state.error!!, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header: image, blurb, donate (series pages), count
                if (state.imageUrl.isNotBlank() || state.description.isNotBlank() || state.isSeries) {
                    item {
                        Column {
                            if (state.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = state.imageUrl,
                                    contentDescription = state.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            if (state.hasRemaster) {
                                RemasterBadge()
                                Spacer(Modifier.height(8.dp))
                            }
                            if (state.description.isNotBlank()) {
                                Text(state.description, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(12.dp))
                            }
                            if (state.isSeries) {
                                Button(onClick = onDonateClick, modifier = Modifier.fillMaxWidth()) { Text("Donate") }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                if (state.items.isEmpty()) {
                    item { EmptyState("No talks found", Modifier.height(200.dp)) }
                }
                if (state.totalItems > 0) {
                    item {
                        Text(
                            "${state.totalItems} ${if (state.isSeries) "talks" else "items"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(state.items, key = { _, it -> "${it.path}|${it.catNum}" }) { index, item ->
                    if (index >= state.items.size - 6) {
                        LaunchedEffect(state.items.size) { viewModel.loadMore() }
                    }
                    ListItemCard(item, onClick = { onItemClick(item) })
                }
                if (state.isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

/** Talk, series or speaker/place tile in the shared list style. */
@Composable
fun ListItemCard(item: SearchResult, onClick: () -> Unit) {
    val subtitle = buildList {
        if (item.year > 0) add(item.year.toString())
        if (item.centre.isNotBlank()) add(item.centre)
    }.joinToString(" · ").ifBlank { null }
    TalkCard(
        title = item.title,
        speaker = when {
            item.isSeries -> "Series${if (item.speaker.isNotBlank()) " · ${item.speaker}" else ""}"
            else -> item.speaker
        },
        imageUrl = item.imageUrl,
        subtitle = if (item.isBrowseLink) null else subtitle,
        onClick = onClick,
    )
}
