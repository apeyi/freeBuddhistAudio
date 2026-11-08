package com.dharmachakra.fba_android.ui.home

/**
 * Where a featured post opens (pure, tested): talks and series natively, the Digital Legacy page
 * natively, anything else on the website. Band colours step through the palette's deep shades
 * until FBA sends a colour per post. Mirrors iOS `FeaturedLink`.
 */
sealed class FeaturedTarget {
    data class Talk(val catNum: String) : FeaturedTarget()
    data class Series(val catNum: String) : FeaturedTarget()
    data object DigitalLegacy : FeaturedTarget()
    data class Web(val url: String) : FeaturedTarget()
}

object FeaturedLink {
    fun target(url: String): FeaturedTarget {
        val num = Regex("[?&]num=([^&#]+)").find(url)?.groupValues?.get(1)
        return when {
            url.contains("/audio/details") && num != null -> FeaturedTarget.Talk(num)
            url.contains("/series/details") && num != null -> FeaturedTarget.Series(num)
            Regex("/digital-legacy/?($|[?#])").containsMatchIn(url) -> FeaturedTarget.DigitalLegacy
            else -> FeaturedTarget.Web(url)
        }
    }

    /** Darker shades from the FBA palette for the colour band under each card's image. */
    val BANDS = listOf(0xFF3A8425, 0xFFA85D21, 0xFF006AA3, 0xFFD0397C, 0xFF4A7C8C, 0xFF8B3DF6, 0xFF368257, 0xFF5761F9)

    fun band(index: Int): Long = BANDS[Math.floorMod(index, BANDS.size)]
}
