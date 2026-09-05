package com.fba.app.data.repository

import com.fba.app.data.local.AppSettings
import com.fba.app.data.local.ContentCache
import com.fba.app.data.remote.FBAScraper
import com.fba.app.data.remote.SiteMenuParser
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
