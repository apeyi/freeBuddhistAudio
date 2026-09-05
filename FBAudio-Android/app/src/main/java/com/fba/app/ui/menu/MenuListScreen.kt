package com.fba.app.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fba.app.data.remote.FBAScraper
import com.fba.app.data.repository.ContentRepository
import com.fba.app.domain.model.MenuNode
import com.fba.app.ui.components.CollectionTile
import com.fba.app.ui.components.EmptyState
import com.fba.app.ui.components.ErrorMessage
import com.fba.app.ui.components.LoadingIndicator
import com.fba.app.ui.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MenuListUiState(
    val title: String = "",
    val nodes: List<MenuNode> = emptyList(),
    /** normalized browse path → image URL (People / Places only) */
    val images: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Shows one section (or sub-section) of the website's curated menu: Themes, People, Places… */
@HiltViewModel
class MenuListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val content: ContentRepository,
) : ViewModel() {
    private val path: List<String> = (savedStateHandle.get<String>("path") ?: "").split('|').filter { it.isNotBlank() }
    private val title: String = savedStateHandle.get<String>("title")?.ifBlank { null } ?: path.lastOrNull() ?: ""

    private val _uiState = MutableStateFlow(MenuListUiState(title = title))
    val uiState: StateFlow<MenuListUiState> = _uiState

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val nodes = content.getNodeChildren(path)
                    .filterNot { it.isPlaceholder && !it.hasChildren }
                _uiState.value = _uiState.value.copy(nodes = nodes, isLoading = false)
                // People and Places entries get the images FBA shows in its own indexes
                val indexType = when (path.firstOrNull()?.lowercase()) {
                    "people" -> "speakers"
                    "places" -> "places"
                    else -> null
                }
                if (indexType != null) {
                    _uiState.value = _uiState.value.copy(images = content.getIndexImages(indexType))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = friendlyError(e))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuListScreen(
    onNodeClick: (MenuNode) -> Unit,
    onBack: () -> Unit,
    viewModel: MenuListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(state.title.replaceFirstChar { it.uppercase() }) },
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
            state.nodes.isEmpty() -> EmptyState("Nothing here yet", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.nodes, key = { it.label + it.link }) { node ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNodeClick(node) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Small generated artwork so the list reads like the website's tiles
                            CollectionTile(
                                title = "",
                                slug = node.collectionSlug ?: node.label,
                                imageUrl = state.images[FBAScraper.normalizeBrowsePathStatic(node.link)] ?: "",
                                onClick = { onNodeClick(node) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer12()
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    node.label.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (node.hasChildren) {
                                    Text(
                                        "${node.children.size} entries",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer12() = androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
