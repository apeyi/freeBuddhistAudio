package com.dharmachakra.fba_android.data.remote.api

import com.dharmachakra.fba_android.api.apis.CatalogueApi
import com.dharmachakra.fba_android.api.apis.SearchApi
import com.dharmachakra.fba_android.api.apis.ServiceApi
import com.dharmachakra.fba_android.api.models.CollectionPage
import com.dharmachakra.fba_android.api.models.ListItem
import com.dharmachakra.fba_android.api.models.ListItemKind
import com.dharmachakra.fba_android.api.models.ListItemPage
import com.dharmachakra.fba_android.api.models.ListKind
import com.dharmachakra.fba_android.api.models.MenuNodeKind
import com.dharmachakra.fba_android.api.models.SearchKind
import com.dharmachakra.fba_android.api.models.Series
import com.dharmachakra.fba_android.data.remote.FBAScraper
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.domain.model.DigitalLegacy
import com.dharmachakra.fba_android.domain.model.ListPage
import com.dharmachakra.fba_android.domain.model.MenuNode
import com.dharmachakra.fba_android.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import com.dharmachakra.fba_android.api.models.MenuNode as V2MenuNode

/**
 * Reads the website catalogue from `/api/v2` via the generated client and maps
 * the v2 models back onto the app's existing domain shapes (`ListPage`,
 * `SearchResult`, `MenuNode`, `DigitalLegacy`), so the screens and their routing
 * (`ContentSource`) are unchanged — only the data source moved off the scraper.
 * Anonymous only: talk detail / account data stay on the cookie path until the
 * v2 auth endpoints ship.
 */
@Singleton
class V2Catalogue @Inject constructor(
    private val catalogue: CatalogueApi,
    private val service: ServiceApi,
    private val search: SearchApi,
) {
    // ---- lists / collections / browse / series -----------------------------

    suspend fun getPage(source: ContentSource, page: Int): ListPage = withContext(Dispatchers.IO) {
        when (source) {
            is ContentSource.ApiCollection -> apiCollection(source, page)
            is ContentSource.NamedCollection -> catalogue.getCollection(source.slug, page, PAGE).toListPage()
            is ContentSource.Browse -> browse(source.path, page)
            is ContentSource.Series -> catalogue.getSeries(extractNum(source.path)).toListPage()
        }
    }

    private fun apiCollection(source: ContentSource.ApiCollection, page: Int, pageSize: Int = PAGE): ListPage {
        listKindFor(source.type)?.let { return catalogue.getList(it, page, pageSize).toListPage(source.title) }
        val index = indexPage(source.type, page, pageSize)
        return index?.toListPage(source.title) ?: ListPage(emptyList(), 0, page, source.title)
    }

    private fun indexPage(type: String, page: Int, pageSize: Int): ListItemPage? =
        when (type.lowercase().replace("_", "")) {
            "speakers" -> catalogue.listSpeakers(null, page, pageSize)
            "places" -> catalogue.listPlaces(null, page, pageSize)
            "years" -> catalogue.listYears(page, pageSize)
            "themes" -> catalogue.listThemes(page, pageSize)
            "genres" -> catalogue.listGenres(null, page, pageSize)
            else -> null
        }

    private fun browse(path: String, page: Int): ListPage {
        val q = path.substringAfter('?', "")
        fun param(k: String): String? =
            Regex("(?:^|&)$k=([^&]*)").find(q)?.groupValues?.get(1)
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                ?.takeIf { it.isNotBlank() }
        return when {
            param("s") != null -> catalogue.listSpeakerTalks(param("s")!!, page, PAGE).toListPage()
            param("p") != null -> catalogue.listPlaceTalks(param("p")!!, page, PAGE).toListPage()
            param("th") != null -> catalogue.listThemeTalks(param("th")!!, page, PAGE).toListPage()
            param("genre") != null -> catalogue.listGenreTalks(param("genre")!!, page, PAGE).toListPage()
            param("lang") != null -> catalogue.listLanguageTalks(param("lang")!!, page, PAGE).toListPage()
            param("y") != null -> param("y")!!.toIntOrNull()?.let { catalogue.listYearTalks(it, page, PAGE).toListPage() }
                ?: ListPage(emptyList(), 0, page)
            param("cat") != null -> apiCollection(ContentSource.ApiCollection(param("cat")!!), page)
            else -> ListPage(emptyList(), 0, page)
        }
    }

    // ---- menu --------------------------------------------------------------

    suspend fun getMenu(): List<MenuNode> = withContext(Dispatchers.IO) {
        service.getMenu().items.map { it.toDomainNode() }
    }

    /** Synthesise the domain MenuNode's `link` from the v2 kind/target so the
     *  existing MenuNode.toSource()/collectionSlug/isExternal keep working. */
    private fun V2MenuNode.toDomainNode(): MenuNode {
        val t = target ?: ""
        val link = when (kind) {
            MenuNodeKind.LIST -> "/browse?cat=$t"
            MenuNodeKind.COLLECTION -> "/collection/$t"
            MenuNodeKind.SERIES -> "/series/details?num=$t"
            MenuNodeKind.SPEAKER -> "/browse?s=$t"
            MenuNodeKind.PLACE -> "/browse?p=$t"
            MenuNodeKind.THEME -> "/browse?th=$t"
            MenuNodeKind.YEAR -> "/browse?y=$t"
            MenuNodeKind.LANGUAGE -> "/browse?lang=$t"
            MenuNodeKind.GROUP -> "#"
            MenuNodeKind.PAGE, MenuNodeKind.EXTERNAL, MenuNodeKind.UNKNOWN -> webUrl ?: "#"
        }
        return MenuNode(label = label, link = link, om = omOnly, children = children.map { it.toDomainNode() })
    }

    // ---- indexes (for the People/Places lists + fuzzy search) --------------

    /** Whole speaker/place/… index in one call (used for A–Z lists and local fuzzy matching). */
    suspend fun getIndexEntries(type: String): List<SearchResult> = withContext(Dispatchers.IO) {
        (indexPage(type, 1, INDEX_PAGE)?.items ?: emptyList()).map { it.toSearchResult() }
    }

    /** Index name → image, keyed by normalized browse path (matches the menu nodes). */
    suspend fun getIndexImages(type: String): Map<String, String> = withContext(Dispatchers.IO) {
        (indexPage(type, 1, INDEX_PAGE)?.items ?: emptyList())
            .mapNotNull { item ->
                val r = item.toSearchResult()
                r.imageUrl.takeIf { it.isNotBlank() }?.let { FBAScraper.normalizeBrowsePathStatic(r.path) to it }
            }.toMap()
    }

    // ---- search ------------------------------------------------------------

    suspend fun searchTalks(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        search.search(SearchKind.TALK, query, pageSize = 50).items.map { it.item.toSearchResult() }
    }

    suspend fun searchSeries(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        search.search(SearchKind.SERIES, query, pageSize = 50).items.map { it.item.toSearchResult() }
    }

    // ---- digital legacy ----------------------------------------------------

    suspend fun getDigitalLegacy(): DigitalLegacy = withContext(Dispatchers.IO) {
        val p = service.getDigitalLegacyPage()
        DigitalLegacy(
            title = p.title,
            description = p.bodyText,
            sampleCatNum = p.sample.catNum,
            seriesPath = "/series/details?num=${p.seriesCatNum}",
        )
    }

    // ---- mappers: v2 -> domain ---------------------------------------------

    private fun ListItem.toSearchResult(): SearchResult {
        val path = when (kind) {
            ListItemKind.TALK -> "/audio/details?num=$id"
            ListItemKind.SERIES -> "/series/details?num=$id"
            ListItemKind.SPEAKER -> "/browse?s=$id"
            ListItemKind.PLACE -> "/browse?p=$id"
            ListItemKind.THEME -> "/browse?th=$id"
            ListItemKind.GENRE -> "/browse?genre=$id"
            ListItemKind.YEAR -> "/browse?y=$id"
            ListItemKind.COLLECTION -> "/collection/$id"
            ListItemKind.UNKNOWN -> webUrl
        }
        return SearchResult(
            catNum = id,
            title = title,
            speaker = subtitle ?: "",
            imageUrl = image ?: "",
            path = path,
            year = year ?: 0,
            centre = placeName ?: "",
            omOnly = omOnly,
        )
    }

    private fun ListItemPage.toListPage(titleOverride: String? = null) = ListPage(
        items = items.map { it.toSearchResult() },
        totalItems = paging.total,
        page = paging.page,
        title = titleOverride?.takeIf { it.isNotBlank() } ?: title ?: "",
    )

    private fun CollectionPage.toListPage() = ListPage(
        items = items.map { it.toSearchResult() },
        totalItems = paging.total,
        page = paging.page,
        title = title,
        description = descriptionText ?: "",
        imageUrl = image ?: "",
    )

    private fun Series.toListPage() = ListPage(
        items = members.map { it.toSearchResult() },
        totalItems = members.size,
        page = 1,
        title = title,
        description = blurbText,
        imageUrl = image ?: "",
        hasRemaster = hasRemaster,
        omOnly = omOnly,
    )

    private fun extractNum(path: String): String =
        Regex("num=([^&]+)").find(path)?.groupValues?.get(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: path

    private fun listKindFor(type: String): ListKind? = when (type.lowercase().replace("_", "")) {
        "latest" -> ListKind.LATEST
        "popular" -> ListKind.POPULAR
        "titles" -> ListKind.TITLES
        "introductions" -> ListKind.INTRODUCTIONS
        "meditation" -> ListKind.MEDITATION
        "serieslatest" -> ListKind.SERIES_LATEST
        "seriessangharakshita" -> ListKind.SERIES_SANGHARAKSHITA
        "seriesotherspeakers" -> ListKind.SERIES_OTHER_SPEAKERS
        "allseries" -> ListKind.ALL_SERIES
        "seriesthemes" -> ListKind.SERIES_THEMES
        "ordertalks", "order" -> ListKind.ORDER_TALKS
        else -> null
    }

    companion object {
        private const val PAGE = ListPage.PAGE_SIZE
        private const val INDEX_PAGE = 1000   // indexes allow up to 1000; fetch whole
    }
}
