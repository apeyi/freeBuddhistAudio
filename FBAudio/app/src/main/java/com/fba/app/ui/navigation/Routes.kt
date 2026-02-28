package com.fba.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val DETAIL = "detail/{catNum}"
    const val DOWNLOADS = "downloads"
    const val PLAYER = "player"
    const val BROWSE_SPEAKER = "browse_speaker/{speakerName}"
    const val BROWSE_SERIES = "browse_series/{seriesName}"
    const val TRANSCRIPT = "transcript/{transcriptUrl}"

    fun detail(catNum: String) = "detail/$catNum"
    fun browseForSpeaker(name: String) = "browse_speaker/${java.net.URLEncoder.encode(name, "UTF-8")}"
    fun browseForSeries(name: String) = "browse_series/${java.net.URLEncoder.encode(name, "UTF-8")}"
    fun transcript(url: String) = "transcript/${java.net.URLEncoder.encode(url, "UTF-8")}"
}
