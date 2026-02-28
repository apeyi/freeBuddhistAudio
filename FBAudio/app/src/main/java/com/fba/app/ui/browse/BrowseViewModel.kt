package com.fba.app.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.BrowseCategory
import com.fba.app.domain.model.CategoryType
import com.fba.app.domain.model.MitraStudyData
import com.fba.app.domain.model.SangharakshitaData
import com.fba.app.domain.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { DEFAULT, YEAR_DESC, YEAR_ASC, TITLE_AZ }

data class BrowseUiState(
    val categories: List<BrowseCategory> = emptyList(),
    val selectedCategory: BrowseCategory? = null,
    val talks: List<SearchResult> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val isLoadingTalks: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val totalTalkCount: Int = 0,
    val sortOrder: SortOrder = SortOrder.DEFAULT,
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

    // Pagination state — kept separate so loadMore can reference it
    private var paginationApiUrl: String = ""
    private var paginationQueryString: String = ""
    private var autoLoadJob: Job? = null
    // Full unfiltered list when all items are loaded (for year/decade filtering)
    private var allItems: List<SearchResult> = emptyList()
    // Mitra Study navigation stack for nested back navigation
    private val mitraCategoryStack = mutableListOf<BrowseCategory>()
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
                val speakerName: String? = savedStateHandle["speakerName"]
                if (!speakerName.isNullOrBlank()) {
                    val decodedName = java.net.URLDecoder.decode(speakerName, "UTF-8")
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
                    val decodedSeries = java.net.URLDecoder.decode(seriesName, "UTF-8")
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
                    error = e.message ?: "Failed to load categories",
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
                sortOrder = SortOrder.DEFAULT,
            )
            return
        }

        // Mitra Study: show years as sub-categories
        if (category.type == CategoryType.MITRA_STUDY) {
            mitraCategoryStack.add(category)
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                categories = MitraStudyData.yearCategories(),
                isLoadingTalks = false,
                isLoadingCategories = false,
                talks = emptyList(),
                totalTalkCount = 0,
                showingSubCategories = true,
            )
            return
        }

        // Mitra Year: show modules as sub-categories
        if (category.type == CategoryType.MITRA_YEAR) {
            mitraCategoryStack.add(category)
            val year = category.browseUrl.substringAfterLast("/").toIntOrNull() ?: 1
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                categories = MitraStudyData.moduleCategories(year),
                isLoadingTalks = false,
                isLoadingCategories = false,
                talks = emptyList(),
                totalTalkCount = 0,
                showingSubCategories = true,
            )
            return
        }

        // Mitra Module: show hardcoded talks
        if (category.type == CategoryType.MITRA_MODULE) {
            mitraCategoryStack.add(category)
            val moduleTalks = MitraStudyData.moduleTalksAsSearchResults(category.id)
            allItems = moduleTalks
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                talks = moduleTalks,
                totalTalkCount = moduleTalks.size,
                hasMore = false,
                isLoadingTalks = false,
                allItemsLoaded = true,
                showingSubCategories = false,
                sortOrder = SortOrder.DEFAULT,
                availableDecades = emptyList(),
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
                sortOrder = SortOrder.DEFAULT,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            isLoadingTalks = true,
            talks = emptyList(),
            hasMore = false,
            totalTalkCount = 0,
            sortOrder = SortOrder.DEFAULT,
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
                    error = e.message ?: "Failed to load talks",
                )
            }
        }
    }

    /** Background-load all remaining items in batches of 24, updating UI after each batch. */
    private fun autoLoadAllRemaining(cacheKey: String, firstPage: com.fba.app.data.remote.BrowsePage) {
        autoLoadJob = viewModelScope.launch {
            var loaded = allItems.toMutableList()
            val total = firstPage.totalItems
            while (loaded.size < total) {
                val batchSize = minOf(24, total - loaded.size)
                val newItems = repository.fetchMoreItems(
                    paginationApiUrl, paginationQueryString,
                    loaded.size + 1, batchSize,
                )
                if (newItems.isEmpty()) break
                loaded.addAll(newItems)
                allItems = loaded.toList()
                val partialDecades = computeDecades(allItems)
                // Save cache incrementally so it persists even if user navigates away
                repository.setCachedBrowse(cacheKey, allItems)
                _uiState.value = _uiState.value.copy(
                    talks = applyFilters(allItems, _uiState.value.sortOrder, _uiState.value.selectedDecade, _uiState.value.selectedYear),
                    hasMore = loaded.size < total,
                    isLoadingMore = loaded.size < total,
                    availableDecades = partialDecades,
                )
            }
            // All loaded — compute decades and cache
            val decades = computeDecades(allItems)
            _uiState.value = _uiState.value.copy(
                allItemsLoaded = true,
                hasMore = false,
                isLoadingMore = false,
                availableDecades = decades,
                totalTalkCount = allItems.size,
            )
            repository.setCachedBrowse(cacheKey, allItems)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || paginationApiUrl.isBlank()) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val startIndex = state.talks.size + 1 // API is 1-indexed
                val newItems = repository.fetchMoreItems(
                    apiBaseUrl = paginationApiUrl,
                    browseQueryString = paginationQueryString,
                    startIndex = startIndex,
                    count = 24,
                )
                val allTalks = state.talks + newItems
                _uiState.value = _uiState.value.copy(
                    talks = applySortOrder(allTalks, state.sortOrder),
                    isLoadingMore = false,
                    hasMore = allTalks.size < state.totalTalkCount,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        val state = _uiState.value
        val filtered = applyFilters(allItems.ifEmpty { state.talks }, order, state.selectedDecade, state.selectedYear)
        _uiState.value = state.copy(talks = filtered, sortOrder = order)
    }

    fun selectDecade(decade: Int?) {
        val state = _uiState.value
        val years = if (decade != null) {
            allItems.mapNotNull { if (it.year in decade until decade + 10) it.year else null }
                .distinct().sorted()
        } else emptyList()
        val filtered = applyFilters(allItems, state.sortOrder, decade, null)
        _uiState.value = state.copy(
            selectedDecade = decade,
            selectedYear = null,
            availableYears = years,
            talks = filtered,
        )
    }

    fun selectYear(year: Int?) {
        val state = _uiState.value
        val filtered = applyFilters(allItems, state.sortOrder, state.selectedDecade, year)
        _uiState.value = state.copy(selectedYear = year, talks = filtered)
    }

    private fun applyFilters(
        items: List<SearchResult>,
        order: SortOrder,
        decade: Int?,
        year: Int?,
    ): List<SearchResult> {
        var result = items
        if (year != null) {
            result = result.filter { it.year == year }
        } else if (decade != null) {
            result = result.filter { it.year in decade until decade + 10 }
        }
        return applySortOrder(result, order)
    }

    private fun applySortOrder(talks: List<SearchResult>, order: SortOrder): List<SearchResult> {
        return when (order) {
            SortOrder.DEFAULT -> talks
            SortOrder.YEAR_DESC -> talks.sortedByDescending { it.year }
            SortOrder.YEAR_ASC -> talks.sortedBy { if (it.year == 0) Int.MAX_VALUE else it.year }
            SortOrder.TITLE_AZ -> talks.sortedBy { it.title.lowercase() }
        }
    }

    private fun computeDecades(items: List<SearchResult>): List<Int> {
        val years = items.mapNotNull { if (it.year > 0) it.year else null }.distinct()
        if (years.size <= 10) return emptyList() // don't show decades if <10 different years
        return years.map { (it / 10) * 10 }.distinct().sorted()
    }

    fun clearSelection() {
        // Don't cancel autoLoadJob — let background loading continue so cache gets populated
        allItems = emptyList()

        // Mitra nested back: pop stack and go back one level
        if (mitraCategoryStack.isNotEmpty()) {
            mitraCategoryStack.removeLastOrNull()
            val parent = mitraCategoryStack.lastOrNull()
            if (parent != null) {
                // Re-select the parent to show its sub-categories
                mitraCategoryStack.removeLast() // will be re-added by selectCategory
                selectCategory(parent)
                return
            }
            // Stack empty — back to root categories
            mitraCategoryStack.clear()
        }

        _uiState.value = _uiState.value.copy(
            selectedCategory = null,
            categories = rootCategories,
            talks = emptyList(),
            hasMore = false,
            totalTalkCount = 0,
            sortOrder = SortOrder.DEFAULT,
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
