package com.fba.app.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.repository.ContentRepository
import com.fba.app.domain.model.ContentSource
import com.fba.app.domain.model.ListPage
import com.fba.app.domain.model.SearchResult
import com.fba.app.ui.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListUiState(
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val isSeries: Boolean = false,
    val hasRemaster: Boolean = false,
    val items: List<SearchResult> = emptyList(),
    val totalItems: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

/** A paginated list of talks/series from any [ContentSource]. */
@HiltViewModel
class ListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val content: ContentRepository,
) : ViewModel() {
    private val source: ContentSource? = savedStateHandle.get<String>("source")?.let { ContentSource.decode(it) }
    private val initialTitle: String = savedStateHandle.get<String>("title") ?: ""

    private val _uiState = MutableStateFlow(ListUiState(title = initialTitle, isSeries = source is ContentSource.Series))
    val uiState: StateFlow<ListUiState> = _uiState

    private var lastPage: ListPage? = null

    init { load() }

    fun load() {
        val src = source ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "Nothing to show")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val page = content.getPage(src, 1)
                lastPage = page
                _uiState.value = _uiState.value.copy(
                    title = page.title.ifBlank { initialTitle },
                    description = page.description,
                    imageUrl = page.imageUrl,
                    hasRemaster = page.hasRemaster,
                    items = page.items,
                    totalItems = page.totalItems,
                    hasMore = page.hasMore,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = friendlyError(e))
            }
        }
    }

    fun loadMore() {
        val src = source ?: return
        val prev = lastPage ?: return
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val page = content.getPage(src, prev.page + 1, prev)
                lastPage = page.copy(apiUrl = page.apiUrl.ifBlank { prev.apiUrl }, apiQuery = page.apiQuery.ifBlank { prev.apiQuery })
                val seen = _uiState.value.items.mapTo(HashSet()) { "${it.path}|${it.catNum}" }
                val fresh = page.items.filter { seen.add("${it.path}|${it.catNum}") }
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items + fresh,
                    hasMore = page.hasMore && page.items.isNotEmpty(),
                    isLoadingMore = false,
                )
            } catch (_: Exception) {
                // Keep what we have; the user can scroll again to retry.
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }
}
