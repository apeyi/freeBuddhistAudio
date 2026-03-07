package com.fba.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fba.app.ui.components.EmptyState
import com.fba.app.ui.components.ErrorMessage
import com.fba.app.ui.components.LoadingIndicator
import com.fba.app.ui.components.TalkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onTalkClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Dismiss keyboard when search results arrive
    LaunchedEffect(state.hasSearched, state.isLoading) {
        if (state.hasSearched && !state.isLoading) {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(state.navigateToCatNum) {
        state.navigateToCatNum?.let {
            onTalkClick(it)
            viewModel.clearNavigation()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Search field
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search or paste URL") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.search()
                        focusManager.clearFocus()
                    }),
                )
            }

            // Mode toggle chips
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.searchMode == SearchMode.ALL,
                        onClick = { viewModel.setSearchMode(SearchMode.ALL) },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = state.searchMode == SearchMode.BY_SPEAKER,
                        onClick = { viewModel.setSearchMode(SearchMode.BY_SPEAKER) },
                        label = { Text("By speaker") },
                    )
                }
            }

            // Keyword filter (only in speaker mode after results load)
            if (state.searchMode == SearchMode.BY_SPEAKER && state.hasSearched && state.results.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = state.keywordFilter,
                        onValueChange = { viewModel.onKeywordFilterChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Filter by keyword") },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                        trailingIcon = {
                            if (state.keywordFilter.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onKeywordFilterChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                    )
                }
                // Show count
                if (state.keywordFilter.isNotBlank() || state.totalSpeakerTalks > 0) {
                    item {
                        val countText = if (state.keywordFilter.isNotBlank())
                            "${state.filteredResults.size} of ${state.results.size} talks"
                        else if (state.isLoadingMore)
                            "${state.results.size} of ${state.totalSpeakerTalks} talks (loading...)"
                        else
                            "${state.results.size} talks"
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Results
            when {
                state.isLoading -> item { LoadingIndicator() }
                state.error != null -> item {
                    ErrorMessage(
                        message = state.error!!,
                        onRetry = { viewModel.search() },
                    )
                }
                state.hasSearched && state.filteredResults.isEmpty() -> item {
                    EmptyState(
                        if (state.keywordFilter.isNotBlank())
                            "No talks matching \"${state.keywordFilter}\""
                        else
                            "No results found for \"${state.query}\""
                    )
                }
                else -> {
                    items(state.filteredResults, key = { it.catNum }) { result ->
                        TalkCard(
                            title = result.title,
                            speaker = result.speaker,
                            imageUrl = result.imageUrl,
                            subtitle = if (result.year > 0) result.year.toString() else null,
                            onClick = { onTalkClick(result.catNum) },
                        )
                    }
                }
            }
        }
    }
}
