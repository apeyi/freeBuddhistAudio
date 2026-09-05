package com.fba.app.domain.model

data class Track(
    val title: String,
    val durationSeconds: Int,
    val audioUrl: String,
    /** Website track id — needed for resume-position sync with the FBA account. */
    val trackId: String = "",
    /** Digitally remastered version of this track, or "" when none exists. */
    val remasterAudioUrl: String = "",
    val remasterDurationSeconds: Int = 0,
) {
    val hasRemaster: Boolean get() = remasterAudioUrl.isNotBlank()
}

/** The listener's saved position on the FBA website (only present when logged in). */
data class Checkpoint(
    val trackId: String,
    val timeSeconds: Int,
)

data class Talk(
    val catNum: String,
    val title: String,
    val speaker: String,
    val year: Int,
    val genre: String,
    val durationSeconds: Int,
    val imageUrl: String,
    val audioUrl: String,
    val description: String,
    val tracks: List<Track> = emptyList(),
    val transcriptUrl: String = "",
    val series: String = "",
    val seriesHref: String = "",
    /** Restricted to members of the Triratna Buddhist Order. */
    val omOnly: Boolean = false,
    val checkpoint: Checkpoint? = null,
) {
    val hasRemaster: Boolean get() = tracks.any { it.hasRemaster }
}

data class SearchResult(
    val catNum: String,
    val title: String,
    val speaker: String,
    val imageUrl: String,
    val path: String,
    val year: Int = 0,
    /** Centre / place the talk was recorded at (used by the language filter). */
    val centre: String = "",
    val omOnly: Boolean = false,
) {
    val isSeries: Boolean get() = path.contains("/series/")
    /** Speaker / place / year / genre entries link to a browse listing, not a talk. */
    val isBrowseLink: Boolean get() = path.contains("/browse")
    val isTalk: Boolean get() = !isSeries && !isBrowseLink
}

data class BrowseCategory(
    val id: String,
    val name: String,
    val type: CategoryType,
    // Full absolute browse URL to fetch talks for this category
    val browseUrl: String = "",
)

enum class CategoryType {
    SPEAKER, THEME, SERIES, PLACE, YEAR, LANGUAGE,
    SANGHARAKSHITA,
}
