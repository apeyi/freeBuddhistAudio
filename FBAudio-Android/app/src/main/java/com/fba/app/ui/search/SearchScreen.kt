package com.fba.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
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
import com.fba.app.domain.model.SearchResult
import com.fba.app.ui.list.ListItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onTalkClick: (String) -> Unit,
    /** Series, speaker, place and collection results — routed by the caller. */
    onItemClick: (SearchResult) -> Unit = {},
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Dismiss keyboard when search results arrive
    LaunchedEffect(state.hasSearched, state.isLoading) {
        if (state.hasSearched && !state.isLoading) focusManager.clearFocus()
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search talks and series") },
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

            // Category chips: All + every category that has results
            if (state.chips.isNotEmpty() && !state.isLoading) {
                item {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.chips.size) { i ->
                            val f = state.chips[i]
                            FilterChip(
                                selected = state.effectiveFilter == f,
                                onClick = { viewModel.setFilter(f) },
                                label = { Text(f.label) },
                            )
                        }
                    }
                }
            }

            fun open(result: SearchResult) = if (result.isTalk) onTalkClick(result.catNum) else onItemClick(result)

            when {
                state.isLoading -> item { LoadingIndicator() }
                state.error != null -> item {
                    ErrorMessage(message = state.error!!, onRetry = { viewModel.search() })
                }
                state.hasSearched && state.results.isEmpty -> item {
                    EmptyState("No results found for \"${state.query}\"")
                }
                else -> {
                    val sections = if (state.effectiveFilter == SearchFilter.ALL) state.results.available
                        else listOf(state.effectiveFilter)
                    val showHeaders = sections.size > 1
                    for (section in sections) {
                        val items = state.results.of(section)
                        if (showHeaders) item(key = "header:$section") { SectionHeader(section.label, items.size) }
                        items(items, key = { "$section|${it.path}|${it.catNum}" }) { result ->
                            ListItemCard(result, onClick = { open(result) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
