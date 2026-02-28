package com.fba.app.domain.model

data class Track(
    val title: String,
    val durationSeconds: Int,
    val audioUrl: String,
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
)

data class SearchResult(
    val catNum: String,
    val title: String,
    val speaker: String,
    val imageUrl: String,
    val path: String,
    val year: Int = 0,
)

data class BrowseCategory(
    val id: String,
    val name: String,
    val type: CategoryType,
    // Full absolute browse URL to fetch talks for this category
    val browseUrl: String = "",
)

enum class CategoryType {
    SPEAKER, THEME, SERIES, PLACE, YEAR, LANGUAGE,
    MITRA_STUDY, MITRA_YEAR, MITRA_MODULE,
    SANGHARAKSHITA,
}
