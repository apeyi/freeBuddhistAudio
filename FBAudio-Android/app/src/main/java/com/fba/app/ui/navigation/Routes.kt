package com.fba.app.ui.navigation

import android.net.Uri
import com.fba.app.domain.model.ContentSource

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DETAIL = "detail/{catNum}"
    const val DOWNLOADS = "downloads"
    const val JOIN = "join"
    const val MY_FBA = "my_fba"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val BROWSE_SPEAKER = "browse_speaker/{speakerName}"
    const val BROWSE_SERIES = "browse_series/{seriesName}"
    const val TRANSCRIPT = "transcript/{transcriptUrl}?catNum={catNum}"
    const val SANGHARAKSHITA_BY_YEAR = "sangharakshita_by_year"
    const val SANGHARAKSHITA_SERIES = "sangharakshita_series"
    const val COLLECTIONS = "collections"
    const val DIGITAL_LEGACY = "digital_legacy"
    /** A list of talks/series from any [ContentSource]; `source` is [ContentSource.encode]d. */
    const val LIST = "list/{source}?title={title}"
    /** A section of the website's curated menu: `path` is "|"-joined labels, e.g. "themes". */
    const val MENU = "menu/{path}?title={title}"

    // Args are encoded with Uri.encode exactly once; Navigation Compose decodes
    // them exactly once on receipt — ViewModels must NOT decode again.
    // (URLEncoder was wrong here: it produces application/x-www-form-urlencoded
    // "+" for spaces, which Uri.decode leaves alone.)
    fun detail(catNum: String) = "detail/${Uri.encode(catNum)}"
    fun browseForSpeaker(name: String) = "browse_speaker/${Uri.encode(name)}"
    fun browseForSeries(name: String) = "browse_series/${Uri.encode(name)}"
    fun transcript(url: String, catNum: String = "") =
        "transcript/${Uri.encode(url)}?catNum=${Uri.encode(catNum)}"
    fun list(source: ContentSource, title: String = "") =
        "list/${Uri.encode(source.encode())}?title=${Uri.encode(title)}"
    fun menu(path: List<String>, title: String = "") =
        "menu/${Uri.encode(path.joinToString("|"))}?title=${Uri.encode(title)}"

    /** Series links from talk pages / search are hrefs; resolve them to the series list. */
    fun seriesFromHref(href: String): String {
        val path = href.removePrefix("https://www.freebuddhistaudio.com")
        return if (path.startsWith("/series/details")) list(ContentSource.Series(path))
        else browseForSeries(href)
    }
}
