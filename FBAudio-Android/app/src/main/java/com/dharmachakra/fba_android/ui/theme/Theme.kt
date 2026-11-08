package com.dharmachakra.fba_android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun Long.toColor(): Color = Color(this)

/** The current page's colour family as Compose colours (hourly accent, gold or Order blue). */
@Immutable
data class PageColors(val base: Color, val deep: Color, val tint: Color) {
    companion object {
        fun of(set: AccentSet) = PageColors(set.base.toColor(), set.deep.toColor(), set.tint.toColor())
    }
}

/** Neutral greys from the palette, for text and surfaces that never follow the accent. */
object FbaColors {
    val black = FbaPalette.BLACK.toColor()
    val nearBlack = FbaPalette.GREY_900.toColor()
    val grey700 = FbaPalette.GREY_700.toColor()
    val meta = FbaPalette.GREY_600.toColor()
    val grey500 = FbaPalette.GREY_500.toColor()
    val divider = FbaPalette.GREY_200.toColor()
    val circle = FbaPalette.GREY_100.toColor()
    val grey50 = FbaPalette.GREY_50.toColor()
    val white = FbaPalette.WHITE.toColor()
    val destructive = FbaPalette.CONTRAST_RED.toColor()
    val orderOnly = PageColors.of(OrderOnly)
    val gold = PageColors.of(RemasterGold)
}

/** Layout and shape tokens (tokens.json `layout`, Android column). */
object FbaDimens {
    val margin = 16.dp
    val radiusArtwork = 16.dp
    val radiusTile = 16.dp
    val radiusThumb = 8.dp
    val radiusChip = 8.dp
    val radiusSheet = 28.dp
    val playCircle = 36.dp
    val toolCircle = 52.dp
    val minTouch = 48.dp
}

val LocalPageColors = staticCompositionLocalOf { PageColors.of(AccentCycle.default.set) }
private val LocalHourlyAccent = staticCompositionLocalOf { AccentCycle.default.set }

/**
 * Light scheme mapped from one colour family. The design binds only accent-deep (buttons,
 * links, active underlines, readable white text) and accent-tint (pale fills, nav indicator),
 * so Material's primary is the deep shade; base is kept for artwork via [LocalPageColors].
 */
private fun lightScheme(set: AccentSet): ColorScheme {
    val deep = set.deep.toColor()
    val tint = set.tint.toColor()
    return lightColorScheme(
        primary = deep,
        onPrimary = FbaColors.white,
        primaryContainer = tint,
        onPrimaryContainer = deep,
        secondary = deep,
        onSecondary = FbaColors.white,
        secondaryContainer = tint,          // M3 nav-bar indicator, selected chips
        onSecondaryContainer = deep,        // M3 nav-bar active icon
        tertiary = deep,
        onTertiary = FbaColors.white,
        background = FbaColors.white,
        onBackground = FbaColors.black,     // text is black or grey, never the accent
        surface = FbaColors.white,
        onSurface = FbaColors.black,
        surfaceVariant = FbaColors.circle,
        onSurfaceVariant = FbaColors.meta,
        surfaceContainerLowest = FbaColors.white,
        surfaceContainerLow = FbaColors.grey50,
        surfaceContainer = FbaColors.circle,
        surfaceContainerHigh = FbaColors.circle,
        surfaceContainerHighest = FbaColors.divider,
        outline = FbaColors.grey500,
        outlineVariant = FbaColors.divider,
        error = FbaColors.destructive,
    )
}

/**
 * Dark mode isn't in the v1.0 designs; this keeps the app usable for people who run their
 * phone dark: neutral surfaces, the brighter base shade as primary (deep is too dark on black).
 */
private fun darkScheme(set: AccentSet): ColorScheme {
    val base = set.base.toColor()
    return darkColorScheme(
        primary = base,
        onPrimary = FbaColors.black,
        primaryContainer = set.deep.toColor(),
        onPrimaryContainer = FbaColors.white,
        secondary = base,
        onSecondary = FbaColors.black,
        secondaryContainer = set.deep.toColor(),
        onSecondaryContainer = FbaColors.white,
        tertiary = base,
        background = Color(0xFF121212),
        onBackground = Color(0xFFEDEDED),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFFB0B0B0),
        surfaceContainerLowest = Color(0xFF0E0E0E),
        surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainer = Color(0xFF222222),
        surfaceContainerHigh = Color(0xFF2A2A2A),
        surfaceContainerHighest = Color(0xFF333333),
        outline = Color(0xFF8A8A8A),
        outlineVariant = Color(0xFF3A3A3A),
        error = Color(0xFFFF6B6B),
    )
}

private val FbaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(FbaDimens.radiusChip),     // M3 filter chips
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(FbaDimens.radiusArtwork),
    extraLarge = RoundedCornerShape(FbaDimens.radiusSheet), // sheets and dialogs
)

/** The hourly accent, re-evaluated at the top of each hour. */
@Composable
fun rememberHourlyAccent(): AccentSet {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(AccentCycle.millisToNextHour(System.currentTimeMillis()) + 500)
            now = System.currentTimeMillis()
        }
    }
    return AccentCycle.modeFor(now).set
}

@Composable
private fun ProvideScheme(set: AccentSet, darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(set) else lightScheme(set),
        shapes = FbaShapes,
    ) {
        CompositionLocalProvider(LocalPageColors provides PageColors.of(set), content = content)
    }
}

/** App root theme: hourly accent by default; [accent] overrides it (tests, previews). */
@Composable
fun FBATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentSet? = null,
    content: @Composable () -> Unit,
) {
    val hourly = accent ?: rememberHourlyAccent()
    CompositionLocalProvider(LocalHourlyAccent provides hourly) {
        ProvideScheme(hourly, darkTheme, content)
    }
}

/**
 * Re-themes a page: remastered talks/series go gold everywhere on the page, Order-only
 * features go Order blue. Everything inside reads MaterialTheme / [LocalPageColors] as usual.
 */
@Composable
fun PageColorScope(theme: PageTheme, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    if (theme == PageTheme.HOURLY) { content(); return }
    ProvideScheme(accentFor(theme, LocalHourlyAccent.current), darkTheme, content)
}
