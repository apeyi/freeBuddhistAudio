package com.dharmachakra.fba_android.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dharmachakra.fba_android.data.local.RecentlyListenedDao
import com.dharmachakra.fba_android.data.local.RecentlyListenedEntity
import com.dharmachakra.fba_android.data.remote.api.V2Catalogue
import com.dharmachakra.fba_android.data.repository.ContentRepository
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.domain.model.MenuNode
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.ui.detail.TalkProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A curated collection (Start here, Themes) with where it opens. */
data class ExploreCollection(val title: String, val source: ContentSource)

data class ExploreUiState(
    val continueListening: List<RecentlyListenedEntity> = emptyList(),
    /** Nothing played yet: "Start here" replaces Continue listening. */
    val isNewListener: Boolean = false,
    val startHere: List<ExploreCollection> = emptyList(),
    val newOnFba: List<SearchResult> = emptyList(),
    val popular: List<SearchResult> = emptyList(),
    val remastered: List<SearchResult> = emptyList(),
    val latestSeries: List<SearchResult> = emptyList(),
    val speakers: List<SearchResult> = emptyList(),
    val themes: List<ExploreCollection> = emptyList(),
    val languages: List<V2Catalogue.LanguageEntry> = emptyList(),
    val archiveYear: Int? = null,
    val archiveTalks: List<SearchResult> = emptyList(),
    val archiveCount: Int = 0,
    val places: List<SearchResult> = emptyList(),
    /** A random start into the rainbow, so Explore's panels vary between visits. */
    val rainbowStart: Int = (0..9).random(),
)

/**
 * Explore (SPEC "Explore"): rows to discover more of the archive. Each row loads on its own, so
 * one slow request doesn't hold the page.
 */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val content: ContentRepository,
    private val recentlyListened: RecentlyListenedDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState

    private fun update(f: (ExploreUiState) -> ExploreUiState) { _uiState.value = f(_uiState.value) }

    init {
        viewModelScope.launch {
            recentlyListened.getRecentlyListened().collect { rows ->
                val unfinished = com.dharmachakra.fba_android.ui.myfba.MyFbaRules.continueListening(rows, System.currentTimeMillis())
                update { it.copy(continueListening = unfinished.take(6), isNewListener = rows.isEmpty()) }
            }
        }
        launchRow { val v = page(ContentSource.ApiCollection("latest")).filter { it.isTalk() }.take(10); update { it.copy(newOnFba = v) } }
        launchRow { val v = page(ContentSource.ApiCollection("popular")).filter { it.isTalk() }.take(10); update { it.copy(popular = v) } }
        launchRow { val v = page(ContentSource.ApiCollection("series_latest")).take(10); update { it.copy(latestSeries = v) } }
        launchRow { val v = page(ContentSource.seriesByCatNum("X16")).take(6); update { it.copy(remastered = v) } }
        launchRow {
            val sections = content.getHomeSections()
            update { it.copy(speakers = sections["speakers"].orEmpty().take(10), places = sections["places"].orEmpty().take(10)) }
        }
        launchRow {
            val v = content.getLanguages().filter { l -> l.code != "en" }
                .sortedBy { l -> LANGUAGE_ORDER.indexOf(l.code).let { i -> if (i < 0) LANGUAGE_ORDER.size else i } }
            update { it.copy(languages = v) }
        }
        launchRow {
            val menu = content.getMenu()
            update { it.copy(themes = THEMES, startHere = startHere(menu)) }
        }
        launchRow {
            // From the archive: a different year each visit, one with enough talks to show.
            val years = content.getIndexEntries("years").mapNotNull { it.title.trim().toIntOrNull() }.filter { it in 1965..2019 }.shuffled()
            // A year with enough talks to be worth a row (up to a few tries).
            for (year in years.take(4)) {
                val p = content.getPage(ContentSource.Browse("/browse?y=$year"), 1)
                if (p.totalItems >= 8 || year == years.take(4).last()) {
                    update { it.copy(archiveYear = year, archiveTalks = p.items.filter { r -> r.isTalk() }.take(4), archiveCount = p.totalItems) }
                    break
                }
            }
        }
    }

    private fun launchRow(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    private suspend fun page(source: ContentSource) = content.getPage(source, 1).items

    private fun SearchResult.isTalk() = !isSeries && !isCollection && !isBrowseLink

    companion object {
        /**
         * Explore Themes (SPEC "Collection links"): a curated set of the site's main English themes,
         * all curated Collections (/collection/...), never genre tags.
         */
        val THEMES = listOf(
            "Metta" to "positive-emotion", "Bodhisattva Ideal" to "bodhisattva-ideal", "Myth and Symbol" to "myth-symbol",
            "Wisdom" to "wisdom", "Going for Refuge" to "going-for-refuge", "Death" to "death-dying",
            "Friendship" to "friendship-kalyana-mitrata", "Ethics" to "ethics", "Tara" to "tara", "Pure Land" to "pureland",
        ).map { (title, slug) -> ExploreCollection(title, ContentSource.NamedCollection(slug)) }

        /** Talks in other languages: the design's order first, then the rest. */
        val LANGUAGE_ORDER = listOf("hi", "de", "es", "fr", "no", "nl", "pl", "pt", "ru", "fi", "sv")
    }

    /** Start here: six picks from the collections menu's items 2 to 7 and their sub-collections. */
    private fun startHere(menu: List<MenuNode>): List<ExploreCollection> {
        val groups = com.dharmachakra.fba_android.data.remote.SiteMenuParser.section(menu, "collections")?.children.orEmpty().drop(1).take(6)
        return groups.flatMap { g -> if (g.hasChildren) g.children else listOf(g) }
            .mapNotNull { n -> n.collectionSlug?.let { ExploreCollection(n.label.replaceFirstChar { c -> c.uppercase() }, ContentSource.NamedCollection(it)) } }
            .shuffled()
            .take(6)
    }
}
