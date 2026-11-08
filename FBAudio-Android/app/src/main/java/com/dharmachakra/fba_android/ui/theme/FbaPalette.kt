package com.dharmachakra.fba_android.ui.theme

import java.time.Instant
import java.time.ZoneId

/**
 * Colour tokens from the v1.0 design (docs/design/tokens.json, Figma "FBA Palette").
 * Kept as ARGB longs with no Compose dependency so the cycle logic is plain-JVM testable;
 * [Theme.kt] turns them into Compose colours. Never hard-code a colour that has a token here.
 */
object FbaPalette {
    // Neutral
    const val BLACK = 0xFF000000
    const val GREY_900 = 0xFF262626   // "FBA black": speaker names, near-black links
    const val GREY_700 = 0xFF575757
    const val GREY_600 = 0xFF757575   // meta text (length, year, place)
    const val GREY_500 = 0xFF949494
    const val GREY_200 = 0xFFE5E5E5   // dividers
    const val GREY_100 = 0xFFF2F2F2   // tool circles, play circles, Now Playing bar
    const val GREY_50 = 0xFFF8F8F8
    const val WHITE = 0xFFFFFFFF

    // UX extras
    const val SANGHARAKSHITA_ORANGE = 0xFFD53424
    const val HEART_RED = 0xFFFF4031
    const val CONTRAST_RED = 0xFFDB0000   // destructive actions (Sign out, Remove)

    /** Proposed addition for artwork gradients only: a partner or glow, never under white text. */
    const val PROPOSED_YELLOW = 0xFFFFC93C
}

/** One colour family: [base] for artwork, [deep] for buttons/links/readable white text, [tint] for pale fills. */
data class AccentSet(val base: Long, val deep: Long, val tint: Long)

/** The site's brand colours, each with base/deep/tint shades. */
enum class Brand(val set: AccentSet) {
    FBA_BLUE(AccentSet(0xFF00ADEF, 0xFF007AA8, 0xFFE0F5FD)),
    FBA_GREEN(AccentSet(0xFF3E8C27, 0xFF3A8425, 0xFFE8F1E5)),
    ORDER_ONLY_BLUE(AccentSet(0xFF006AA3, 0xFF006AA3, 0xFFE0EDF4)),
    FBA_ORANGE(AccentSet(0xFFEC5E2B, 0xFFCD4412, 0xFFFDECE6)),
    FBA_CHESTNUT(AccentSet(0xFFA85D21, 0xFFA85D21, 0xFFF5ECE4)),
    FBA_FUSCHIA(AccentSet(0xFFD13D7F, 0xFFD0397C, 0xFFF9E8F0)),
    FBA_LIGHT_GREEN(AccentSet(0xFF5ABA83, 0xFF368257, 0xFFEBF7F0)),
    FBA_LILAC(AccentSet(0xFF979DFB, 0xFF5761F9, 0xFFF3F3FF)),
    FBA_LIGHT_MAGENTA(AccentSet(0xFFFF42D3, 0xFFD600A4, 0xFFFFE8FA)),
    FBA_PURPLE(AccentSet(0xFF8B3DF6, 0xFF8B3DF6, 0xFFF1E8FE)),
    FBA_NEW_RED(AccentSet(0xFFEA1221, 0xFFE51220, 0xFFFCE3E4)),
    FBA_SIERRA(AccentSet(0xFF659CAE, 0xFF4A7C8C, 0xFFEDF3F5)),
}

/** Digital Legacy gold: replaces the accent everywhere on a remastered talk or series. */
val RemasterGold = AccentSet(base = 0xFFDBAF55, deep = 0xFF8A6A1F, tint = 0xFFF8EFDD)

/** Order-only features and the "Order member" badge always use this, never the hourly accent. */
val OrderOnly = Brand.ORDER_ONLY_BLUE.set

/**
 * The hourly accent, as on freebuddhistaudio.com: one colour mode per hour, default FBA Blue.
 *
 * The website picks its colour server-side and doesn't publish the rule (asked FBA to expose it
 * in /meta), so for now this steps through the design's mode order by hour of day, UK time.
 * Everything that needs the accent goes through [modeFor], so matching the site later is a
 * one-line change.
 */
object AccentCycle {
    /** Mode order from tokens.json `accent.modes`. */
    val modes: List<Brand> = listOf(
        Brand.FBA_BLUE, Brand.FBA_GREEN, Brand.ORDER_ONLY_BLUE, Brand.FBA_ORANGE, Brand.FBA_CHESTNUT,
        Brand.FBA_FUSCHIA, Brand.FBA_LIGHT_GREEN, Brand.FBA_LILAC, Brand.FBA_NEW_RED, Brand.FBA_PURPLE,
    )
    val default: Brand = Brand.FBA_BLUE
    val siteZone: ZoneId = ZoneId.of("Europe/London")

    fun modeFor(epochMillis: Long, zone: ZoneId = siteZone): Brand {
        val hour = Instant.ofEpochMilli(epochMillis).atZone(zone).hour
        return modes[hour % modes.size]
    }

    /** Milliseconds until the next whole hour, when the accent may change. */
    fun millisToNextHour(epochMillis: Long): Long {
        val hourMs = 3_600_000L
        return hourMs - Math.floorMod(epochMillis, hourMs)
    }
}

/** Which colour family a page uses. */
enum class PageTheme { HOURLY, REMASTERED, ORDER_ONLY }

/** The colour family for [theme] given the current hourly [hourly] accent. */
fun accentFor(theme: PageTheme, hourly: AccentSet): AccentSet = when (theme) {
    PageTheme.HOURLY -> hourly
    PageTheme.REMASTERED -> RemasterGold
    PageTheme.ORDER_ONLY -> OrderOnly
}
