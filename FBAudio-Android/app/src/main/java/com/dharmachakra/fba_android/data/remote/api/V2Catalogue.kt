package com.dharmachakra.fba_android.data.remote.api

import com.dharmachakra.fba_android.api.apis.CatalogueApi
import com.dharmachakra.fba_android.api.models.CollectionPage
import com.dharmachakra.fba_android.api.models.ListItem
import com.dharmachakra.fba_android.api.models.ListItemKind
import com.dharmachakra.fba_android.api.models.ListItemPage
import com.dharmachakra.fba_android.api.models.ListKind
import com.dharmachakra.fba_android.api.models.Series
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.domain.model.ListPage
import com.dharmachakra.fba_android.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the website catalogue (lists, collections, browse listings, series)
 * from `/api/v2` via the generated client and maps the v2 models back onto the
 * app's existing domain shapes (`ListPage` / `SearchResult`), so the screens and
 * their routing (`ContentSource`) are unchanged — only the data source moved off
 * the HTML scraper.
 */
@Singleton
class V2Catalogue @Inject constructor(private val catalogue: CatalogueApi) {

    suspend fun getPage(source: ContentSource, page: Int): ListPage = withContext(Dispatchers.IO) {
        when (source) {
            is ContentSource.ApiCollection -> apiCollection(source, page)
            is ContentSource.NamedCollection -> catalogue.getCollection(source.slug, page, PAGE).toListPage()
            is ContentSource.Browse -> browse(source.path, page)
            is ContentSource.Series -> catalogue.getSeries(extractNum(source.path)).toListPage()
        }
    }

    private fun apiCollection(source: ContentSource.ApiCollection, page: Int): ListPage {
        listKindFor(source.type)?.let { return catalogue.getList(it, page, PAGE).toListPage(source.title) }
        val index = when (source.type.lowercase().replace("_", "")) {
            "speakers" -> catalogue.listSpeakers(null, page, PAGE)
            "places" -> catalogue.listPlaces(null, page, PAGE)
            "years" -> catalogue.listYears(page, PAGE)
            "themes" -> catalogue.listThemes(page, PAGE)
            "genres" -> catalogue.listGenres(null, page, PAGE)
            else -> null
        }
        return index?.toListPage(source.title) ?: ListPage(emptyList(), 0, page, source.title)
    }

    private fun browse(path: String, page: Int): ListPage {
        val query = path.substringAfter('?', "")
        fun param(k: String): String? =
            Regex("(?:^|&)$k=([^&]*)").find(query)?.groupValues?.get(1)
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                ?.takeIf { it.isNotBlank() }
        return when {
            param("s") != null -> catalogue.listSpeakerTalks(param("s")!!, page, PAGE).toListPage()
            param("p") != null -> catalogue.listPlaceTalks(param("p")!!, page, PAGE).toListPage()
            param("th") != null -> catalogue.listThemeTalks(param("th")!!, page, PAGE).toListPage()
            param("genre") != null -> catalogue.listGenreTalks(param("genre")!!, page, PAGE).toListPage()
            param("y") != null -> param("y")!!.toIntOrNull()?.let { catalogue.listYearTalks(it, page, PAGE).toListPage() }
                ?: ListPage(emptyList(), 0, page)
            param("cat") != null -> apiCollection(ContentSource.ApiCollection(param("cat")!!), page)
            else -> ListPage(emptyList(), 0, page)
        }
    }

    // ---- mappers: v2 models -> domain models -------------------------------

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
            centre = placeName ?: "",   // language filter matches on the centre name
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
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?: path

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
    }
}
