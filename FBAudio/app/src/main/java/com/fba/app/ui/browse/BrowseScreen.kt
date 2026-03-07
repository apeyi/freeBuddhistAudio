package com.fba.app.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fba.app.domain.model.CategoryType
import com.fba.app.ui.components.ErrorMessage
import com.fba.app.ui.components.LoadingIndicator
import com.fba.app.ui.components.TalkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onTalkClick: (String) -> Unit,
    onBack: () -> Unit,
    initialSangharakshitaByYear: Boolean = false,
    initialSangharakshitaSeries: Boolean = false,
    initialMitraStudy: Boolean = false,
    alwaysPopOnBack: Boolean = false,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val hasInitialSelection = initialSangharakshitaByYear || initialSangharakshitaSeries || initialMitraStudy
    // Apply initial navigation if requested
    androidx.compose.runtime.LaunchedEffect(Unit) {
        when {
            initialSangharakshitaByYear -> viewModel.selectSangharakshitaByYear()
            initialSangharakshitaSeries -> viewModel.selectSangharakshitaSeries()
            initialMitraStudy -> viewModel.selectMitraStudy()
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.selectedCategory != null && state.totalTalkCount > 0)
                            "${state.selectedCategory!!.name} (${state.totalTalkCount})"
                        else
                            state.selectedCategory?.name ?: "Browse",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (alwaysPopOnBack) {
                            onBack()
                        } else if (hasInitialSelection) {
                            // If showing sub-content within the initial selection, go back within it
                            // Otherwise pop back to the previous screen entirely
                            val canGoBack = when {
                                initialSangharakshitaSeries && state.selectedCategory?.id?.startsWith("sang_series_") == true -> true
                                initialMitraStudy && state.showingSubCategories -> true
                                else -> false
                            }
                            if (canGoBack) viewModel.clearSelection()
                            else onBack()
                        } else if (state.selectedCategory != null || state.showingSubCategories) {
                            viewModel.clearSelection()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        when {
            state.isLoadingCategories -> LoadingIndicator(Modifier.padding(padding))
            state.error != null && state.selectedCategory == null -> ErrorMessage(
                message = state.error!!,
                onRetry = { viewModel.loadCategories() },
                modifier = Modifier.padding(padding),
            )
            state.selectedCategory == null || state.showingSubCategories -> {
                // Show categories
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.categories, key = { it.id }) { category ->
                        ListItem(
                            headlineContent = { Text(category.name) },
                            supportingContent = {
                                Text(
                                    text = when (category.type) {
                                        CategoryType.MITRA_STUDY -> "Study Course"
                                        CategoryType.MITRA_YEAR -> "Study Year"
                                        CategoryType.MITRA_MODULE -> "Module"
                                        else -> category.type.name.lowercase().replaceFirstChar { it.uppercase() }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.clickable { viewModel.selectCategory(category) },
                        )
                    }
                }
            }
            state.isLoadingTalks -> LoadingIndicator(Modifier.padding(padding))
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Decade chips (shown when all items loaded + >10 different years)
                    if (state.availableDecades.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = state.selectedDecade == null,
                                    onClick = { viewModel.selectDecade(null) },
                                    label = { Text("All") },
                                )
                                for (decade in state.availableDecades) {
                                    FilterChip(
                                        selected = state.selectedDecade == decade,
                                        onClick = { viewModel.selectDecade(decade) },
                                        label = { Text("${decade}s") },
                                    )
                                }
                            }
                        }
                    }

                    // Year chips within selected decade
                    if (state.availableYears.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = state.selectedYear == null,
                                    onClick = { viewModel.selectYear(null) },
                                    label = { Text("All ${state.selectedDecade}s") },
                                )
                                for (year in state.availableYears) {
                                    FilterChip(
                                        selected = state.selectedYear == year,
                                        onClick = { viewModel.selectYear(year) },
                                        label = { Text("$year") },
                                    )
                                }
                            }
                        }
                    }

                    // Talk count label when filtered
                    if (state.allItemsLoaded && (state.selectedDecade != null || state.selectedYear != null)) {
                        item {
                            Text(
                                text = "${state.talks.size} talks",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    items(state.talks, key = { it.catNum }) { talk ->
                        TalkCard(
                            title = talk.title,
                            speaker = talk.speaker,
                            imageUrl = talk.imageUrl,
                            subtitle = if (talk.year > 0) talk.year.toString() else null,
                            onClick = { onTalkClick(talk.catNum) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    // Load more footer (only when not auto-loading all)
                    if ((state.hasMore || state.isLoadingMore) && !state.allItemsLoaded) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator()
                                } else {
                                    OutlinedButton(onClick = { viewModel.loadMore() }) {
                                        Text("Load more (${state.talks.size} of ${state.totalTalkCount})")
                                    }
                                }
                            }
                        }
                    }

                    // Loading indicator while auto-loading remaining items
                    if (state.isLoadingMore && state.allItemsLoaded.not() && state.talks.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Loading all talks... (${state.talks.size}/${state.totalTalkCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
