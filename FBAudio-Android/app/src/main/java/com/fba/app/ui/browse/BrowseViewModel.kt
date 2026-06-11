package com.fba.app.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.BrowseCategory
import com.fba.app.domain.model.CategoryType
import com.fba.app.domain.model.SangharakshitaData
import com.fba.app.domain.model.SearchResult
import com.fba.app.ui.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val categories: List<BrowseCategory> = emptyList(),
    val selectedCategory: BrowseCategory? = null,
    val talks: List<SearchResult> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val isLoadingTalks: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val totalTalkCount: Int = 0,
    val error: String? = null,
    val allItemsLoaded: Boolean = false,
    val availableDecades: List<Int> = emptyList(),
    val selectedDecade: Int? = null,
    val availableYears: List<Int> = emptyList(),
    val selectedYear: Int? = null,
    val showingSubCategories: Boolean = false, // true when showing Mitra year/module sub-categories
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: TalkRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState

    /** Tracks whether the initial selection (e.g. selectSangharakshitaByYear) has already been applied. */
    var hasBeenInitialized = false

    // Pagination state — kept separate so loadMore can reference it
    private var paginationApiUrl: String = ""
    private var paginationQueryString: String = ""
    // Next absolute 1-based page index to fetch. Advanced by the REQUESTED batch
    // size, not the received count — the API skips non-audio pages, so deriving
    // the index from list size drifts and re-fetches items (duplicate-key crash).
    private var nextFetchIndex: Int = 1
    private var autoLoadJob: Job? = null
    // Full unfiltered list when all items are loaded (for year/decade filtering)
    private var allItems: List<SearchResult> = emptyList()
    // Remember the root categories so we can restore them
    private var rootCategories: List<BrowseCategory> = emptyList()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCategories = true, error = null)
            try {
                val categories = repository.getBrowseCategories()
                rootCategories = categories
                _uiState.value = _uiState.value.copy(
                    categories = categories,
                    isLoadingCategories = false,
                )
                // If a speaker name was passed via SavedStateHandle (from BROWSE_SPEAKER route),
                // immediately select that speaker as the category.
                // Nav args arrive already decoded by Navigation Compose — no second decode.
                val speakerName: String? = savedStateHandle["speakerName"]
                if (!speakerName.isNullOrBlank()) {
                    val decodedName = speakerName
                    // Sangharakshita uses hardcoded data
                    if (decodedName.equals("Sangharakshita", ignoreCase = true)) {
                        selectCategory(
                            BrowseCategory(
                                id = "Sangharakshita",
                                name = "Sangharakshita",
                                type = CategoryType.SANGHARAKSHITA,
                                browseUrl = "sang://root",
                            )
                        )
                    } else {
                        selectCategory(
                            BrowseCategory(
                                id = decodedName,
                                name = decodedName,
                                type = CategoryType.SPEAKER,
                                browseUrl = "https://www.freebuddhistaudio.com/browse?s=${java.net.URLEncoder.encode(decodedName, "UTF-8")}&t=audio",
                            )
                        )
                    }
                }

                // If a series name/href was passed via SavedStateHandle (from BROWSE_SERIES route),
                // immediately select that series as the category.
                val seriesName: String? = savedStateHandle["seriesName"]
                if (!seriesName.isNullOrBlank()) {
                    val decodedSeries = seriesName
                    // decodedSeries may be a /series/details href or a series title
                    val isHref = decodedSeries.startsWith("/series/") || decodedSeries.startsWith("http")
                    val seriesBrowseUrl = if (isHref) {
                        if (decodedSeries.startsWith("/")) "https://www.freebuddhistaudio.com$decodedSeries"
                        else decodedSeries
                    } else {
                        "https://www.freebuddhistaudio.com/browse?ser=${java.net.URLEncoder.encode(decodedSeries, "UTF-8")}&t=audio"
                    }
                    selectCategory(
                        BrowseCategory(
                            id = decodedSeries,
                            name = if (isHref) "Series" else decodedSeries,
                            type = CategoryType.SERIES,
                            browseUrl = seriesBrowseUrl,
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingCategories = false,
                    error = friendlyError(e),
                )
            }
        }
    }

    fun selectCategory(category: BrowseCategory) {
        autoLoadJob?.cancel()
        allItems = emptyList()

        // Sangharakshita: hardcoded data, instant load with decade chips
        if (category.type == CategoryType.SANGHARAKSHITA) {
            val sangTalks = SangharakshitaData.allTalksAsSearchResults()
            allItems = sangTalks
            val decades = computeDecades(sangTalks)
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                talks = sangTalks,
                totalTalkCount = sangTalks.size,
                hasMore = false,
                isLoadingTalks = false,
                allItemsLoaded = true,
                showingSubCategories = false,
                availableDecades = decades,
                selectedDecade = null,
                availableYears = emptyList(),
                selectedYear = null,
            )
            return
        }


        // Check in-memory cache first (before setting loading state to avoid flash)
        val cached = repository.getCachedBrowse(category.browseUrl)
        if (cached != null) {
            allItems = cached
            val decades = computeDecades(cached)
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                talks = cached,
                totalTalkCount = cached.size,
                hasMore = false,
                isLoadingTalks = false,
                allItemsLoaded = true,
                showingSubCategories = false,
                availableDecades = decades,
                selectedDecade = null,
                availableYears = emptyList(),
                selectedYear = null,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            isLoadingTalks = true,
            talks = emptyList(),
            hasMore = false,
            totalTalkCount = 0,
            allItemsLoaded = false,
            showingSubCategories = false,
            availableDecades = emptyList(),
            selectedDecade = null,
            availableYears = emptyList(),
            selectedYear = null,
        )

        viewModelScope.launch {
            try {
                val page = if (category.browseUrl.isNotBlank()) {
                    repository.getTalksByBrowseUrl(category.browseUrl)
                } else {
                    val results = repository.searchAudio(category.id)
                    com.fba.app.data.remote.BrowsePage(results, results.size, "", "")
                }
                paginationApiUrl = page.apiBaseUrl
                paginationQueryString = page.browseQueryString
                nextFetchIndex = page.items.size + 1
                allItems = page.items
                val earlyDecades = computeDecades(page.items)
                // Update category name if page returned a title (e.g. series title)
                val updatedCategory = if (page.title.isNotBlank() && category.name != page.title) {
                    category.copy(name = page.title)
                } else category
                _uiState.value = _uiState.value.copy(
                    selectedCategory = updatedCategory,
                    talks = page.items,
                    totalTalkCount = page.totalItems,
                    hasMore = page.hasMore,
                    isLoadingTalks = false,
                    availableDecades = earlyDecades,
                )
                // Auto-load all remaining if this is a large speaker (>20 talks total)
                if (page.hasMore && page.totalItems > 20 && page.apiBaseUrl.isNotBlank()) {
                    autoLoadAllRemaining(category.browseUrl, page)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingTalks = false,
                    error = friendlyError(e),
                )
            }
        }
    }

    /** Append a batch to allItems, de-duplicated by catNum. */
    private fun appendDeduped(newItems: List<SearchResult>) {
        val seen = allItems.mapTo(HashSet()) { it.catNum }
        allItems = allItems + newItems.filter { seen.add(it.catNum) }
    }

    /** Background-load all remaining items in batches of 24, updating UI after each batch. */
    private fun autoLoadAllRemaining(cacheKey: String, firstPage: com.fba.app.data.remote.BrowsePage) {
        autoLoadJob = viewModelScope.launch {
            val total = firstPage.totalItems
            try {
                while (nextFetchIndex <= total) {
                    val batchSize = minOf(24, total - nextFetchIndex + 1)
                    val newItems = repository.fetchMoreItems(
                        paginationApiUrl, paginationQueryString,
                        nextFetchIndex, batchSize,
                    )
                    nextFetchIndex += batchSize
                    appendDeduped(newItems)
                    _uiState.value = _uiState.value.copy(
                        talks = applyFilters(allItems, _uiState.value.selectedDecade, _uiState.value.selectedYear),
                        hasMore = nextFetchIndex <= total,
                        isLoadingMore = nextFetchIndex <= total,
                        availableDecades = computeDecades(allItems),
                    )
                }
                // All loaded — only now is the cache a complete result set
                // (getCachedBrowse treats a hit as fully loaded).
                _uiState.value = _uiState.value.copy(
                    allItemsLoaded = true,
                    hasMore = false,
                    isLoadingMore = false,
                    availableDecades = computeDecades(allItems),
                    totalTalkCount = allItems.size,
                )
                repository.setCachedBrowse(cacheKey, allItems)
            } catch (e: Exception) {
                // Network failure mid-load: keep what we have, leave hasMore=true so
                // the user can Load More to retry. Do NOT cache the truncated list.
                _uiState.value = _uiState.value.copy(
                    hasMore = true,
                    isLoadingMore = false,
                    availableDecades = computeDecades(allItems),
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || paginationApiUrl.isBlank()) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val newItems = repository.fetchMoreItems(
                    apiBaseUrl = paginationApiUrl,
                    browseQueryString = paginationQueryString,
                    startIndex = nextFetchIndex,
                    count = 24,
                )
                nextFetchIndex += 24
                appendDeduped(newItems)
                val s = _uiState.value
                _uiState.value = s.copy(
                    talks = applyFilters(allItems, s.selectedDecade, s.selectedYear),
                    isLoadingMore = false,
                    hasMore = nextFetchIndex <= s.totalTalkCount,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun selectDecade(decade: Int?) {
        val state = _uiState.value
        val years = if (decade != null) {
            allItems.mapNotNull { if (it.year in decade until decade + 10) it.year else null }
                .distinct().sorted()
        } else emptyList()
        val filtered = applyFilters(allItems, decade, null)
        _uiState.value = state.copy(
            selectedDecade = decade,
            selectedYear = null,
            availableYears = years,
            talks = filtered,
        )
    }

    fun selectYear(year: Int?) {
        val state = _uiState.value
        val filtered = applyFilters(allItems, state.selectedDecade, year)
        _uiState.value = state.copy(selectedYear = year, talks = filtered)
    }

    private fun applyFilters(
        items: List<SearchResult>,
        decade: Int?,
        year: Int?,
    ): List<SearchResult> {
        if (year != null) return items.filter { it.year == year }
        if (decade != null) return items.filter { it.year in decade until decade + 10 }
        return items
    }

    private fun computeDecades(items: List<SearchResult>): List<Int> {
        val years = items.mapNotNull { if (it.year > 0) it.year else null }.distinct()
        if (years.size <= 10) return emptyList() // don't show decades if <10 different years
        return years.map { (it / 10) * 10 }.distinct().sorted()
    }

    /** Pre-select Sangharakshita talks with decade/year filters. */
    fun selectSangharakshitaByYear() {
        if (_uiState.value.selectedCategory?.type == CategoryType.SANGHARAKSHITA) return
        selectCategory(
            BrowseCategory(
                id = "Sangharakshita",
                name = "Sangharakshita",
                type = CategoryType.SANGHARAKSHITA,
                browseUrl = "sang://root",
            )
        )
    }

    /** Show Sangharakshita series as a sub-category list. */
    fun selectSangharakshitaSeries() {
        val seriesCategories = SangharakshitaData.seriesAsBrowseCategories()
        _uiState.value = _uiState.value.copy(
            selectedCategory = BrowseCategory(
                id = "sang_series",
                name = "Sangharakshita Series",
                type = CategoryType.SANGHARAKSHITA,
                browseUrl = "sang://series",
            ),
            categories = seriesCategories,
            isLoadingTalks = false,
            isLoadingCategories = false,
            talks = emptyList(),
            totalTalkCount = 0,
            showingSubCategories = true,
        )
    }

    fun clearSelection() {
        // Don't cancel autoLoadJob — let background loading continue so cache gets populated
        allItems = emptyList()

        // Sangharakshita series sub-nav: if showing a series talk list, go back to series list
        val current = _uiState.value.selectedCategory
        if (current != null && current.id.startsWith("sang_series_")) {
            selectSangharakshitaSeries()
            return
        }

        _uiState.value = _uiState.value.copy(
            selectedCategory = null,
            categories = rootCategories,
            talks = emptyList(),
            hasMore = false,
            totalTalkCount = 0,
            allItemsLoaded = false,
            availableDecades = emptyList(),
            selectedDecade = null,
            availableYears = emptyList(),
            selectedYear = null,
            showingSubCategories = false,
        )
        paginationApiUrl = ""
        paginationQueryString = ""
    }
}
