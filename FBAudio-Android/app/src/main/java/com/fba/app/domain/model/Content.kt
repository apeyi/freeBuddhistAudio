package com.fba.app.domain.model

import android.net.Uri

/** One entry of the website's curated side menu (collections, themes, people, places…). */
data class MenuNode(
    val label: String,
    val link: String,
    val om: Boolean = false,
    val children: List<MenuNode> = emptyList(),
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
    val isExternal: Boolean get() = link.startsWith("http") && !link.contains("freebuddhistaudio.com")
    val isPlaceholder: Boolean get() = link.isBlank() || link.startsWith("#")

    /** The /collection/<slug> slug when this entry is a named collection. */
    val collectionSlug: String?
        get() = Regex("/collection/([^/?#]+)").find(link)?.groupValues?.get(1)?.trim()

    /** A [ContentSource] for this entry, or null when it is a placeholder/external link. */
    fun toSource(): ContentSource? {
        val slug = collectionSlug
        val path = link.trim().removePrefix("https://www.freebuddhistaudio.com")
        return when {
            slug != null -> ContentSource.NamedCollection(slug)
            path.startsWith("/series/details") -> ContentSource.Series(path)
            path.startsWith("/browse") || path.startsWith("browse") -> {
                // "/browse?cat=speakers&t=audio" style index pages are the API collections
                val cat = Regex("[?&]cat=([^&]+)").find(path)?.groupValues?.get(1)
                if (cat != null) ContentSource.ApiCollection(cat, label)
                else ContentSource.Browse("/" + path.removePrefix("/"))
            }
            path.startsWith("/audio/details") -> null
            else -> null
        }
    }
}

/**
 * Where a list of talks/series comes from. Encoded into navigation routes with
 * [encode] and decoded with [decode]; keep it a flat string-friendly shape.
 */
sealed class ContentSource {
    /** `/api/v1/collections/{type}` — latest, introductions, speakers, all_series… */
    data class ApiCollection(val type: String, val title: String = "") : ContentSource()
    /** A curated `/collection/{slug}` page (themes, collections, some people/places). */
    data class NamedCollection(val slug: String) : ContentSource()
    /** A `/browse?…` listing: one speaker, place, year or genre. */
    data class Browse(val path: String) : ContentSource()
    /** A `/series/details?num=…` page. */
    data class Series(val path: String) : ContentSource()

    fun encode(): String = when (this) {
        is ApiCollection -> "api|$type|$title"
        is NamedCollection -> "named|$slug|"
        is Browse -> "browse|$path|"
        is Series -> "series|$path|"
    }

    companion object {
        fun decode(encoded: String): ContentSource? {
            val parts = encoded.split('|', limit = 3)
            if (parts.size < 2) return null
            val arg = parts[1]
            val extra = parts.getOrElse(2) { "" }
            return when (parts[0]) {
                "api" -> ApiCollection(arg, extra)
                "named" -> NamedCollection(arg)
                "browse" -> Browse(arg)
                "series" -> Series(arg)
                else -> null
            }
        }

        fun seriesByCatNum(catNum: String) = Series("/series/details?num=${Uri.encode(catNum)}")
    }
}

/** One page of a list plus what's needed to show its header and fetch the next page. */
data class ListPage(
    val items: List<SearchResult>,
    val totalItems: Int,
    val page: Int,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val hasRemaster: Boolean = false,
    val omOnly: Boolean = false,
    /** For browse listings: the API endpoint + query that pages the same list. */
    val apiUrl: String = "",
    val apiQuery: String = "",
) {
    val hasMore: Boolean get() = items.isNotEmpty() && totalItems > 0 && page * PAGE_SIZE < totalItems

    companion object {
        const val PAGE_SIZE = 24
    }
}

/** Content of the website's Digital Legacy page. */
data class DigitalLegacy(
    val title: String,
    val description: String,
    val sampleCatNum: String,
    val seriesPath: String = "/series/details?num=X16",
)
