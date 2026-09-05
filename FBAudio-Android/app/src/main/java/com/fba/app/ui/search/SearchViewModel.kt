package com.fba.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.repository.ContentRepository
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.SangharakshitaData
import com.fba.app.domain.model.SearchResult
import com.fba.app.ui.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All = talks and series; Audio = talks only. A Text tab follows when the server supports transcript search. */
enum class SearchMode { ALL, AUDIO }

data class SearchUiState(
    val query: String = "",
    val searchMode: SearchMode = SearchMode.ALL,
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val navigateToCatNum: String? = null,
) {
    val series: List<SearchResult> get() = if (searchMode == SearchMode.ALL) results.filter { it.isSeries } else emptyList()
    val talks: List<SearchResult> get() = results.filter { !it.isSeries }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TalkRepository,
    private val content: ContentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null
    private val searchCache = mutableMapOf<String, List<SearchResult>>()

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

    fun setSearchMode(mode: SearchMode) {
        if (mode == _uiState.value.searchMode) return
        _uiState.value = _uiState.value.copy(searchMode = mode)
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
        return catNum to text.contains("/series/")
    }

    private suspend fun performSearch(query: String) {
        // Pasted URL → open the talk or series directly
        val urlMatch = extractCatNumFromUrl(query)
        if (urlMatch != null) {
            val (catNum, isSeries) = urlMatch
            if (!isSeries) {
                _uiState.value = _uiState.value.copy(navigateToCatNum = catNum)
                return
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val page = repository.getTalksByBrowseUrl("https://www.freebuddhistaudio.com/series/details?num=$catNum")
                _uiState.value = _uiState.value.copy(results = page.items, isLoading = false, hasSearched = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, hasSearched = true, error = friendlyError(e))
            }
            return
        }

        val cacheKey = query.lowercase()
        searchCache[cacheKey]?.let { cached ->
            _uiState.value = _uiState.value.copy(results = cached, isLoading = false, hasSearched = true, error = null)
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            // "sangharakshita …" queries: the bundled catalogue is instant and complete
            if (query.startsWith("sangharakshita", ignoreCase = true)) {
                val allTalks = SangharakshitaData.allTalksAsSearchResults()
                val words = query.split(Regex("\\s+")).drop(1)
                val results = if (words.isEmpty()) allTalks
                else allTalks.filter { r -> words.all { w -> r.title.contains(w, ignoreCase = true) } }
                if (results.isNotEmpty()) searchCache[cacheKey] = results
                _uiState.value = _uiState.value.copy(results = results, isLoading = false, hasSearched = true)
                return
            }

            val audioDeferred = viewModelScope.async { repository.searchAudio(query) }
            val seriesDeferred = viewModelScope.async {
                try { repository.searchSeries(query) } catch (_: Exception) { emptyList() }
            }
            val audioResults = audioDeferred.await()
            val seriesResults = seriesDeferred.await()
            // Series first, then talks. Dedup key is type-prefixed: series and talk
            // numbers are separate namespaces on FBA.
            val seen = mutableSetOf<String>()
            val merged = (seriesResults + audioResults).filter {
                seen.add("${if (it.isSeries) "s" else "a"}:${it.catNum}")
            }
            val results = content.filterForLanguage(merged)
            if (results.isNotEmpty()) searchCache[cacheKey] = results
            _uiState.value = _uiState.value.copy(results = results, isLoading = false, hasSearched = true)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, hasSearched = true, error = friendlyError(e))
        }
    }
}
