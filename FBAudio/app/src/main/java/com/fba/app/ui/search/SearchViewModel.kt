package com.fba.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.SangharakshitaData
import com.fba.app.domain.model.SearchResult
import com.fba.app.ui.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode { ALL, BY_SPEAKER }

data class SearchUiState(
    val query: String = "",
    val keywordFilter: String = "",
    val searchMode: SearchMode = SearchMode.ALL,
    val results: List<SearchResult> = emptyList(),
    val filteredResults: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val navigateToCatNum: String? = null,
    val totalSpeakerTalks: Int = 0,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TalkRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null
    private var autoLoadJob: Job? = null
    private val searchCache = mutableMapOf<String, List<SearchResult>>()

    // Pagination state for speaker browse
    private var paginationApiUrl = ""
    private var paginationQueryString = ""
    private var allSpeakerItems = mutableListOf<SearchResult>()

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.trim().length >= 3) {
            searchJob = viewModelScope.launch {
                delay(500)
                performSearch(query.trim())
            }
        }
    }

    fun onKeywordFilterChanged(filter: String) {
        _uiState.value = _uiState.value.copy(keywordFilter = filter)
        applyKeywordFilter()
    }

    fun setSearchMode(mode: SearchMode) {
        if (mode == _uiState.value.searchMode) return
        autoLoadJob?.cancel()
        allSpeakerItems.clear()
        _uiState.value = _uiState.value.copy(
            searchMode = mode,
            results = emptyList(),
            filteredResults = emptyList(),
            hasSearched = false,
            keywordFilter = "",
            totalSpeakerTalks = 0,
            error = null,
        )
        val query = _uiState.value.query.trim()
        if (query.length >= 3) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(query) }
        }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    fun clearNavigation() {
        _uiState.value = _uiState.value.copy(navigateToCatNum = null)
    }

    private fun extractCatNumFromUrl(text: String): Pair<String, Boolean>? {
        if (!text.contains("num=")) return null
        val catNum = text.substringAfter("num=").substringBefore("&").substringBefore(" ").trim()
        if (catNum.isBlank()) return null
        val isSeries = text.contains("/series/")
        return catNum to isSeries
    }

    private suspend fun performSearch(query: String) {
        // URL detection
        val urlMatch = extractCatNumFromUrl(query)
        if (urlMatch != null) {
            val (catNum, isSeries) = urlMatch
            if (!isSeries) {
                _uiState.value = _uiState.value.copy(navigateToCatNum = catNum)
                return
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                try {
                    val page = repository.getTalksByBrowseUrl(
                        "https://www.freebuddhistaudio.com/series/details?num=$catNum"
                    )
                    _uiState.value = _uiState.value.copy(
                        results = page.items,
                        filteredResults = page.items,
                        isLoading = false,
                        hasSearched = true,
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, hasSearched = true,
                        error = friendlyError(e),
                    )
                }
                return
            }
        }

        when (_uiState.value.searchMode) {
            SearchMode.ALL -> performGeneralSearch(query)
            SearchMode.BY_SPEAKER -> performSpeakerBrowse(query)
        }
    }

    private suspend fun performGeneralSearch(query: String) {
        val cacheKey = "all:${query.lowercase()}"
        val cached = searchCache[cacheKey]
        if (cached != null) {
            _uiState.value = _uiState.value.copy(
                results = cached, filteredResults = cached,
                isLoading = false, hasSearched = true, error = null,
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            // Sangharakshita queries: use hardcoded + filter
            if (query.startsWith("sangharakshita", ignoreCase = true)) {
                val allTalks = SangharakshitaData.allTalksAsSearchResults()
                val words = query.split(Regex("\\s+")).drop(1)
                val results = if (words.isEmpty()) allTalks
                else allTalks.filter { r ->
                    words.all { w -> r.title.contains(w, ignoreCase = true) }
                }
                if (results.isNotEmpty()) searchCache[cacheKey] = results
                _uiState.value = _uiState.value.copy(
                    results = results, filteredResults = results,
                    isLoading = false, hasSearched = true,
                )
                return
            }

            val results = repository.searchAudio(query)
            if (results.isNotEmpty()) searchCache[cacheKey] = results
            _uiState.value = _uiState.value.copy(
                results = results, filteredResults = results,
                isLoading = false, hasSearched = true,
            )
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false, hasSearched = true,
                error = friendlyError(e),
            )
        }
    }

    private suspend fun performSpeakerBrowse(speakerName: String) {
        autoLoadJob?.cancel()
        allSpeakerItems.clear()

        // Sangharakshita: hardcoded, instant
        if (speakerName.equals("sangharakshita", ignoreCase = true)) {
            val talks = SangharakshitaData.allTalksAsSearchResults()
            allSpeakerItems = talks.toMutableList()
            _uiState.value = _uiState.value.copy(
                results = talks, filteredResults = talks,
                totalSpeakerTalks = talks.size,
                isLoading = false, hasSearched = true,
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, keywordFilter = "")
        try {
            val page = repository.browseBySpeaker(speakerName)
            allSpeakerItems = page.items.toMutableList()
            paginationApiUrl = page.apiBaseUrl
            paginationQueryString = page.browseQueryString
            _uiState.value = _uiState.value.copy(
                results = page.items,
                filteredResults = page.items,
                totalSpeakerTalks = page.totalItems,
                isLoading = false,
                hasSearched = true,
            )
            // Auto-load remaining in background
            if (page.hasMore && page.apiBaseUrl.isNotBlank()) {
                autoLoadRemaining(page.totalItems)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false, hasSearched = true,
                error = friendlyError(e),
            )
        }
    }

    private fun autoLoadRemaining(total: Int) {
        autoLoadJob = viewModelScope.launch {
            while (allSpeakerItems.size < total) {
                val batchSize = minOf(24, total - allSpeakerItems.size)
                val newItems = repository.fetchMoreItems(
                    paginationApiUrl, paginationQueryString,
                    allSpeakerItems.size + 1, batchSize,
                )
                if (newItems.isEmpty()) break
                allSpeakerItems.addAll(newItems)
                _uiState.value = _uiState.value.copy(
                    results = allSpeakerItems.toList(),
                    isLoadingMore = allSpeakerItems.size < total,
                )
                applyKeywordFilter()
            }
            _uiState.value = _uiState.value.copy(isLoadingMore = false)
        }
    }

    private fun applyKeywordFilter() {
        val filter = _uiState.value.keywordFilter.trim()
        val base = if (_uiState.value.searchMode == SearchMode.BY_SPEAKER)
            allSpeakerItems.toList() else _uiState.value.results
        val filtered = if (filter.isBlank()) base
        else {
            val words = filter.split(Regex("\\s+"))
            base.filter { r -> words.all { w -> r.title.contains(w, ignoreCase = true) } }
        }
        _uiState.value = _uiState.value.copy(filteredResults = filtered)
    }
}
