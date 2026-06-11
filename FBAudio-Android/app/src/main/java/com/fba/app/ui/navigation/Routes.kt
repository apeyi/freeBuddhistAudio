package com.fba.app.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DETAIL = "detail/{catNum}"
    const val DOWNLOADS = "downloads"
    const val PLAYER = "player"
    const val BROWSE_SPEAKER = "browse_speaker/{speakerName}"
    const val BROWSE_SERIES = "browse_series/{seriesName}"
    const val TRANSCRIPT = "transcript/{transcriptUrl}?catNum={catNum}"
    const val SANGHARAKSHITA_BY_YEAR = "sangharakshita_by_year"
    const val SANGHARAKSHITA_SERIES = "sangharakshita_series"

    // Args are encoded with Uri.encode exactly once; Navigation Compose decodes
    // them exactly once on receipt — ViewModels must NOT decode again.
    // (URLEncoder was wrong here: it produces application/x-www-form-urlencoded
    // "+" for spaces, which Uri.decode leaves alone.)
    fun detail(catNum: String) = "detail/${Uri.encode(catNum)}"
    fun browseForSpeaker(name: String) = "browse_speaker/${Uri.encode(name)}"
    fun browseForSeries(name: String) = "browse_series/${Uri.encode(name)}"
    fun transcript(url: String, catNum: String = "") =
        "transcript/${Uri.encode(url)}?catNum=${Uri.encode(catNum)}"
}
