package com.dharmachakra.fba_android.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dharmachakra.fba_android.data.repository.ContentRepository
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.domain.model.ListPage
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.ui.friendlyError
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
    /** Series pages. */
    val speaker: String = "",
    val year: Int = 0,
    val bannerUrl: String = "",
    val catNum: String = "",
    /** Saved progress per talk (Recently listened), for listened ticks and Resume Talk n. */
    val saved: Map<String, SeriesProgress.Saved> = emptyMap(),
    /** Small grey label above the title: PLACE, YEAR, SPEAKER, COLLECTION, SERIES. */
    val label: String = "",
) {
    val seriesResume: SeriesProgress.Resume?
        get() = if (isSeries) SeriesProgress.resume(items.map { it.catNum }, saved) else null
}

/** A paginated list of talks/series from any [ContentSource]. */
@HiltViewModel
class ListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val content: ContentRepository,
    private val recentlyListened: com.dharmachakra.fba_android.data.local.RecentlyListenedDao,
    private val talkRepository: com.dharmachakra.fba_android.data.repository.TalkRepository,
    private val downloadRepository: com.dharmachakra.fba_android.data.repository.DownloadRepository,
    private val settings: com.dharmachakra.fba_android.data.local.AppSettings,
) : ViewModel() {

    /** Series: download every talk (fetches each talk's tracks, then queues its download). */
    fun downloadAll() {
        val items = _uiState.value.items.filter { !it.isSeries && !it.isBrowseLink && !it.isCollection }
        viewModelScope.launch {
            for (item in items) {
                if (downloadRepository.getDownload(item.catNum) != null) continue
                val talk = talkRepository.getTalkDetail(item.catNum) ?: continue
                val useRemaster = talk.hasRemaster && settings.useRemaster(talk.catNum)
                downloadRepository.startDownload(
                    catNum = talk.catNum, title = talk.title, speaker = talk.speaker, imageUrl = talk.imageUrl,
                    audioUrl = talk.audioUrl,
                    trackUrls = talk.tracks.map { if (useRemaster && it.hasRemaster) it.remasterAudioUrl else it.audioUrl },
                    transcriptUrl = talk.transcriptUrl,
                    audioVersion = if (useRemaster) "remastered" else "original",
                )
            }
        }
    }
    private val source: ContentSource? = savedStateHandle.get<String>("source")?.let { ContentSource.decode(it) }
    private val initialTitle: String = savedStateHandle.get<String>("title") ?: ""

    private val _uiState = MutableStateFlow(
        ListUiState(title = initialTitle, isSeries = source is ContentSource.Series, label = labelFor(source))
    )
    val uiState: StateFlow<ListUiState> = _uiState

    private var lastPage: ListPage? = null

    init {
        load()
        viewModelScope.launch {
            recentlyListened.getRecentlyListened().collect { rows ->
                _uiState.value = _uiState.value.copy(
                    saved = rows.associate { it.catNum to SeriesProgress.Saved(it.positionMs, it.totalDurationSeconds, it.listenedAt) },
                )
            }
        }
    }

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
                    // orEmpty(): pages cached (Gson) before these fields existed read back null.
                    speaker = page.speaker.orEmpty(),
                    year = page.year,
                    bannerUrl = page.bannerUrl.orEmpty(),
                    catNum = page.catNum.orEmpty(),
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

/** The grey label above a list's title, from where the list comes from. */
fun labelFor(source: ContentSource?): String = when (source) {
    is ContentSource.Series -> "SERIES"
    is ContentSource.NamedCollection -> "COLLECTION"
    is ContentSource.Browse -> when {
        Regex("[?&]p=").containsMatchIn(source.path) -> "PLACE"
        Regex("[?&]y=").containsMatchIn(source.path) -> "YEAR"
        Regex("[?&]s=").containsMatchIn(source.path) -> "SPEAKER"
        Regex("[?&](th|genre)=").containsMatchIn(source.path) -> "THEME"
        Regex("[?&]lang=").containsMatchIn(source.path) -> "LANGUAGE"
        else -> ""
    }
    else -> ""
}
