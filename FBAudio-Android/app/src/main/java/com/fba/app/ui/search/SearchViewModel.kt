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

/** Result categories. Chips are shown for ALL plus every category that has results. */
enum class SearchFilter(val label: String) {
    ALL("All"), TALKS("Talks"), SERIES("Series"), SPEAKERS("Speakers"), PLACES("Places"), COLLECTIONS("Collections")
}

data class SearchResults(
    val talks: List<SearchResult> = emptyList(),
    val series: List<SearchResult> = emptyList(),
    val speakers: List<SearchResult> = emptyList(),
    val places: List<SearchResult> = emptyList(),
    val collections: List<SearchResult> = emptyList(),
) {
    val isEmpty: Boolean get() = talks.isEmpty() && series.isEmpty() && speakers.isEmpty() && places.isEmpty() && collections.isEmpty()

    fun of(filter: SearchFilter): List<SearchResult> = when (filter) {
        SearchFilter.ALL -> emptyList()
        SearchFilter.TALKS -> talks
        SearchFilter.SERIES -> series
        SearchFilter.SPEAKERS -> speakers
        SearchFilter.PLACES -> places
        SearchFilter.COLLECTIONS -> collections
    }

    /** Categories with results, in display order. */
    val available: List<SearchFilter>
        get() = listOf(SearchFilter.SPEAKERS, SearchFilter.PLACES, SearchFilter.COLLECTIONS, SearchFilter.SERIES, SearchFilter.TALKS)
            .filter { of(it).isNotEmpty() }
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val results: SearchResults = SearchResults(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val navigateToCatNum: String? = null,
) {
    /** Chips to show: All + categories with results; falls back to All when the chosen one vanished. */
    val chips: List<SearchFilter> get() = if (results.available.size > 1) listOf(SearchFilter.ALL) + results.available else emptyList()
    val effectiveFilter: SearchFilter get() = if (filter == SearchFilter.ALL || filter in results.available) filter else SearchFilter.ALL
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TalkRepository,
    private val content: ContentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null
    private val searchCache = mutableMapOf<String, SearchResults>()

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

    fun setFilter(filter: SearchFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
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
                _uiState.value = _uiState.value.copy(results = SearchResults(talks = page.items), isLoading = false, hasSearched = true)
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
            // Speakers / places / collections come from FBA's indexes and curated menu
            // (the site's search only returns talks and series).
            val namesDeferred = viewModelScope.async { content.matchNames(query) }

            val talksAndSeries: Pair<List<SearchResult>, List<SearchResult>> =
                if (query.startsWith("sangharakshita", ignoreCase = true)) {
                    // "sangharakshita …" queries: the bundled catalogue is instant and complete
                    val allTalks = SangharakshitaData.allTalksAsSearchResults()
                    val words = query.split(Regex("\\s+")).drop(1)
                    val talks = if (words.isEmpty()) allTalks
                    else allTalks.filter { r -> words.all { w -> r.title.contains(w, ignoreCase = true) } }
                    talks to emptyList()
                } else {
                    // Sequential, not parallel: while logged in the site rotates the session
                    // cookie per response, so concurrent calls would invalidate each other.
                    val audio = repository.searchAudio(query)
                    val series = try { repository.searchSeries(query) } catch (_: Exception) { emptyList() }
                    val seen = mutableSetOf<String>()
                    val merged = content.filterForLanguage((series + audio).filter {
                        seen.add("${if (it.isSeries) "s" else "a"}:${it.catNum}")
                    })
                    merged.filter { !it.isSeries } to merged.filter { it.isSeries }
                }
            val names = namesDeferred.await()
            val results = SearchResults(
                talks = talksAndSeries.first,
                series = talksAndSeries.second,
                speakers = names.speakers,
                places = names.places,
                collections = names.collections,
            )
            if (!results.isEmpty) searchCache[cacheKey] = results
            _uiState.value = _uiState.value.copy(results = results, isLoading = false, hasSearched = true)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, hasSearched = true, error = friendlyError(e))
        }
    }
}
