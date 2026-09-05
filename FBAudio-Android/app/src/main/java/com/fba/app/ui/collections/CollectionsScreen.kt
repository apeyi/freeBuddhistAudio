package com.fba.app.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fba.app.data.repository.ContentRepository
import com.fba.app.domain.model.ContentSource
import com.fba.app.domain.model.MenuNode
import com.fba.app.ui.components.CollectionTile
import com.fba.app.ui.components.ErrorMessage
import com.fba.app.ui.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionsUiState(
    val tiles: List<MenuNode> = emptyList(),
    /** slug → cover image URL, filled in as collection pages load. */
    val covers: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val content: ContentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tiles = content.getCollectionTiles()
                _uiState.value = _uiState.value.copy(tiles = tiles, isLoading = false)
                // Cover images live on each collection's page; fetch them in the
                // background (cached), tiles show generated artwork meanwhile.
                val covers = tiles.mapNotNull { it.collectionSlug }.map { slug ->
                    async {
                        try { slug to content.getPage(ContentSource.NamedCollection(slug), 1).imageUrl }
                        catch (_: Exception) { slug to "" }
                    }
                }.awaitAll().filter { it.second.isNotBlank() }.toMap()
                _uiState.value = _uiState.value.copy(covers = covers)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = friendlyError(e))
            }
        }
    }
}

/** The spec's collections: fixed entry points, shown as the first tiles. */
private data class HubTile(val title: String, val slug: String, val open: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onCollectionClick: (MenuNode) -> Unit,
    onSourceClick: (ContentSource, String) -> Unit,
    onMenuClick: (List<String>, String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val hub = listOf(
        HubTile("Introductions", "introductions") { onSourceClick(ContentSource.ApiCollection("introductions", "Introductions"), "Introductions") },
        HubTile("Meditations", "guided-meditations") { onSourceClick(ContentSource.NamedCollection("guided-meditations"), "Meditations") },
        HubTile("Latest", "latest") { onSourceClick(ContentSource.ApiCollection("latest", "Latest"), "Latest") },
        HubTile("Themes", "themes") { onMenuClick(listOf("themes"), "Themes") },
        HubTile("Series", "all-series") { onSourceClick(ContentSource.ApiCollection("all_series", "Series"), "Series") },
        HubTile("People", "people") { onMenuClick(listOf("people"), "People") },
        HubTile("Places", "places") { onMenuClick(listOf("places"), "Places") },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Collections") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // The hub tiles are static; only FBA's curated tiles need the network, so the
        // grid renders immediately and those fill in (or show a retry) below.
        run {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(hub, key = { "hub:" + it.slug }) { tile ->
                    CollectionTile(title = tile.title, slug = tile.slug, imageUrl = "", onClick = tile.open)
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Text(
                        "From Free Buddhist Audio",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.tiles, key = { it.collectionSlug ?: it.label }) { node ->
                    val slug = node.collectionSlug ?: node.label
                    CollectionTile(
                        title = node.label,
                        slug = slug,
                        imageUrl = state.covers[slug] ?: "",
                        onClick = { onCollectionClick(node) },
                    )
                }
                if (state.isLoading) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) { androidx.compose.material3.CircularProgressIndicator() }
                    }
                }
                state.error?.let { err ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        ErrorMessage(err, onRetry = { viewModel.load() }, modifier = Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
            }
        }
    }
}
