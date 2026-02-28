package com.fba.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fba.app.domain.model.CategoryType
import com.fba.app.ui.browse.BrowseViewModel
import com.fba.app.ui.browse.SortOrder
import com.fba.app.ui.components.ErrorMessage
import com.fba.app.ui.components.LoadingIndicator
import com.fba.app.ui.components.TalkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTalkClick: (String) -> Unit,
    onBrowseClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.selectedCategory != null && state.totalTalkCount > 0)
                            "${state.selectedCategory!!.name} (${state.totalTalkCount})"
                        else
                            state.selectedCategory?.name ?: "Free Buddhist Audio",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (state.selectedCategory != null) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        when {
            state.isLoadingCategories && state.selectedCategory == null -> LoadingIndicator(Modifier.padding(padding))
            state.error != null && state.selectedCategory == null -> ErrorMessage(
                message = state.error!!,
                onRetry = { viewModel.loadCategories() },
                modifier = Modifier.padding(padding),
            )
            state.selectedCategory == null || state.showingSubCategories -> {
                // Show categories list
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
                // Show talks in selected category with sort chips and load more
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Decade chips
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
                }
            }
        }
    }
}
