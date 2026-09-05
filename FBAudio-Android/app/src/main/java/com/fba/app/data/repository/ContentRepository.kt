package com.fba.app.data.repository

import com.fba.app.data.local.AppSettings
import com.fba.app.data.local.ContentCache
import com.fba.app.data.remote.FBAScraper
import com.fba.app.data.remote.SiteMenuParser
import com.fba.app.domain.FuzzyMatch
import com.fba.app.domain.LanguageFilter
import com.fba.app.domain.model.ContentSource
import com.fba.app.domain.model.DigitalLegacy
import com.fba.app.domain.model.ListPage
import com.fba.app.domain.model.MenuNode
import com.fba.app.domain.model.SearchResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Website content beyond single talks: the curated menu, collections, series,
 * Digital Legacy. Cached on disk (24 h) so Home/Collections open offline; when
 * the network fails a stale cache entry is still returned.
 */
class ContentRepository @Inject constructor(
    private val scraper: FBAScraper,
    private val cache: ContentCache,
    private val settings: AppSettings,
) {
    private var menuMemo: List<MenuNode>? = null
    private val menuMutex = Mutex()
    private val pageMemo = LinkedHashMap<String, ListPage>(32, 0.75f, true)

    private class MenuEnvelope(val nodes: List<MenuNode> = emptyList())

    /** The full curated menu (not language-filtered). */
    suspend fun getMenu(forceRefresh: Boolean = false): List<MenuNode> = menuMutex.withLock {
        if (!forceRefresh) menuMemo?.let { return it }
        val cached = cache.get("menu", MenuEnvelope::class.java)
        if (!forceRefresh && cached != null && cached.second && cached.first.nodes.isNotEmpty()) {
            menuMemo = cached.first.nodes
            return cached.first.nodes
        }
        try {
            val fresh = scraper.fetchSiteMenu()
            if (fresh.isNotEmpty()) {
                menuMemo = fresh
                cache.put("menu", MenuEnvelope(fresh))
                return fresh
            }
        } catch (e: Exception) {
            if (cached == null || cached.first.nodes.isEmpty()) throw e
        }
        val stale = cached?.first?.nodes ?: emptyList()
        menuMemo = stale.takeIf { it.isNotEmpty() }
        stale
    }

    /** A top-level menu section ("themes", "people", "places", "collections") filtered for the language setting. */
    suspend fun getSection(label: String): List<MenuNode> {
        val section = SiteMenuParser.section(getMenu(), label) ?: return emptyList()
        return LanguageFilter.filterMenu(section.children, settings.englishOnly.value)
    }

    /** Children of a nested menu path, e.g. ["collections", "meditation & mindfulness"]. */
    suspend fun getNodeChildren(path: List<String>): List<MenuNode> {
        var nodes = getMenu()
        for (label in path) {
            nodes = nodes.firstOrNull { it.label.equals(label, ignoreCase = true) }?.children ?: return emptyList()
        }
        return LanguageFilter.filterMenu(nodes, settings.englishOnly.value)
    }

    /** Curated collections for the Collections grid. */
    suspend fun getCollectionTiles(): List<MenuNode> =
        LanguageFilter.filterMenu(SiteMenuParser.collectionTiles(getMenu()), settings.englishOnly.value)

    /** Load one page of a source. Page 1 of each source is cached for offline use. */
    suspend fun getPage(source: ContentSource, page: Int, previous: ListPage? = null): ListPage {
        val key = "page:${source.encode()}:$page"
        if (page == 1) synchronized(pageMemo) { pageMemo[key] }?.let { return applyLanguage(it) }

        val fetch: suspend () -> ListPage = {
            when (source) {
                is ContentSource.ApiCollection -> scraper.fetchApiCollectionPage(source.type, page, source.title)
                is ContentSource.NamedCollection -> scraper.fetchNamedCollectionPage(source.slug, page)
                is ContentSource.Browse -> scraper.fetchBrowsePage(
                    source.path, page, previous?.apiUrl ?: "", previous?.apiQuery ?: "",
                )
                is ContentSource.Series -> scraper.fetchSeriesPage(source.path)
            }
        }

        val result = if (page == 1) {
            val cached = cache.get(key, ListPage::class.java)
            if (cached != null && cached.second) cached.first
            else try {
                fetch().also { cache.put(key, it) }
            } catch (e: Exception) {
                cached?.first ?: throw e
            }
        } else fetch()

        if (page == 1) synchronized(pageMemo) {
            if (pageMemo.size >= 40) pageMemo.remove(pageMemo.keys.first())
            pageMemo[key] = result
        }
        return applyLanguage(result)
    }

    private suspend fun applyLanguage(page: ListPage): ListPage {
        if (!settings.englishOnly.value) return page
        val menu = try { getMenu() } catch (_: Exception) { emptyList() }
        val filtered = LanguageFilter.filterItems(
            page.items, true,
            LanguageFilter.nonEnglishSpeakers(menu),
            LanguageFilter.nonEnglishCentres(menu),
        )
        return page.copy(items = filtered)
    }

    /** Language-filter any list (e.g. search results). */
    suspend fun filterForLanguage(items: List<SearchResult>): List<SearchResult> {
        if (!settings.englishOnly.value) return items
        val menu = try { getMenu() } catch (_: Exception) { emptyList() }
        return LanguageFilter.filterItems(
            items, true, LanguageFilter.nonEnglishSpeakers(menu), LanguageFilter.nonEnglishCentres(menu),
        )
    }

    private class ImageMap(val images: Map<String, String> = emptyMap())

    /**
     * Images for the curated People / Places lists, taken from FBA's speaker and
     * place indexes (keyed by normalized browse path). Cached; empty on failure.
     */
    suspend fun getIndexImages(type: String): Map<String, String> {
        val key = "images:$type"
        val cached = cache.get(key, ImageMap::class.java)
        if (cached != null && cached.second) return cached.first.images
        return try {
            scraper.fetchIndexImages(type).also { cache.put(key, ImageMap(it)) }
        } catch (_: Exception) {
            cached?.first?.images ?: emptyMap()
        }
    }

    private class EntryList(val items: List<SearchResult> = emptyList())

    /**
     * All speakers or places as browse links (cached daily). Not language-filtered:
     * these are navigation targets matched by name — someone typing "Valencia"
     * wants Valencia regardless of the English-only setting.
     */
    suspend fun getIndexEntries(type: String): List<SearchResult> {
        val key = "index:$type"
        val cached = cache.get(key, EntryList::class.java)
        return if (cached != null && cached.second) cached.first.items else try {
            scraper.fetchIndexEntries(type).also { cache.put(key, EntryList(it)) }
        } catch (_: Exception) {
            cached?.first?.items ?: emptyList()
        }
    }

    /**
     * Index entries plus FBA's curated menu entries for the same section (People /
     * Places), so labels like "talks from east london (lbc)" or "birmingham
     * (england)" are searchable too. Deduplicated by browse path.
     */
    private suspend fun nameEntries(type: String, section: String): List<SearchResult> {
        val index = getIndexEntries(type)
        val byPath = index.associateBy { FBAScraper.normalizeBrowsePathStatic(it.path) }.toMutableMap()
        val menu = try { getMenu() } catch (_: Exception) { emptyList() }
        fun visit(node: MenuNode) {
            val source = node.toSource()
            val path = when (source) {
                is ContentSource.Browse -> source.path
                is ContentSource.NamedCollection -> "/collection/${source.slug}"
                else -> null
            }
            if (path != null) {
                val key = FBAScraper.normalizeBrowsePathStatic(path)
                val existing = byPath[key]
                val label = node.label.replaceFirstChar { it.uppercase() }
                byPath[key] = if (existing != null) {
                    // Keep the index entry (has the image) but make the curated label searchable too
                    if (existing.title.equals(label, ignoreCase = true)) existing else existing.copy(centre = label)
                } else {
                    SearchResult(catNum = path, title = label, speaker = "", imageUrl = "", path = path)
                }
            }
            node.children.forEach { visit(it) }
        }
        SiteMenuParser.section(menu, section)?.children?.forEach { visit(it) }
        return byPath.values.toList()
    }

    /** Curated collections + themes as `/collection/` links (for search). */
    suspend fun getCollectionEntries(): List<SearchResult> {
        val menu = try { getMenu() } catch (_: Exception) { return emptyList() }
        val nodes = SiteMenuParser.collectionTiles(menu) +
            (SiteMenuParser.section(menu, "themes")?.children ?: emptyList()).filter { it.collectionSlug != null }
        return LanguageFilter.filterMenu(nodes, settings.englishOnly.value)
            .distinctBy { it.collectionSlug }
            .map { SearchResult(catNum = it.collectionSlug ?: it.label, title = it.label, speaker = "", imageUrl = "", path = "/collection/${it.collectionSlug}") }
    }

    /** Speakers, places and collections whose name contains the query (for the search screen). */
    data class NameMatches(val speakers: List<SearchResult>, val places: List<SearchResult>, val collections: List<SearchResult>)

    suspend fun matchNames(query: String): NameMatches {
        val q = query.trim().lowercase()
        if (q.length < 2) return NameMatches(emptyList(), emptyList(), emptyList())
        // `centre` temporarily carries the curated label for index entries (see nameEntries).
        // Typo-tolerant, like the site's own talk search ("adhistana" → Adhisthana).
        fun List<SearchResult>.matching() = filter { FuzzyMatch.matches(q, it.title) || FuzzyMatch.matches(q, it.centre) }
            .map { it.copy(centre = "") }
            .sortedWith(compareBy({ !it.title.lowercase().contains(q) }, { it.title.lowercase() }))
            .take(20)
        return NameMatches(
            speakers = nameEntries("speakers", "people").matching(),
            places = nameEntries("places", "places").matching(),
            collections = getCollectionEntries().matching(),
        )
    }

    suspend fun getDigitalLegacy(): DigitalLegacy? {
        val cached = cache.get("digital_legacy", DigitalLegacy::class.java)
        if (cached != null && cached.second) return cached.first
        return try {
            scraper.fetchDigitalLegacy()?.also { cache.put("digital_legacy", it) } ?: cached?.first
        } catch (e: Exception) {
            cached?.first
        }
    }

    /** Drop in-memory page copies (e.g. after the language setting changes). */
    fun invalidateMemo() {
        synchronized(pageMemo) { pageMemo.clear() }
    }
}
