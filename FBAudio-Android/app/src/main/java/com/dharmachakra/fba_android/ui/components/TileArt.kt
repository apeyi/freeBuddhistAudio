package com.dharmachakra.fba_android.ui.components

/**
 * Generated tile artwork (SPEC "Tile artwork", tokens.json `artwork`): colour sets and placement,
 * pure so it's unit-tested; [TileArtwork] draws it. The symbols are Figma placeholders until
 * Dharmachakra's final set arrives (build the recipe, not the icons). Mirrors iOS `TileArt`.
 */
object TileArt {
    /** Main colour blends into [partner] along the diagonal; [glow] is the third colour's patch. */
    data class Colors(val main: Long, val partner: Long, val glow: Long)

    enum class Symbol { WHEEL, LOTUS, VIHARA, BUDDHA }

    /** Where the soft white wash sits (unit coordinates of the tile), clear of symbol and title. */
    data class Wash(val x: Float, val y: Float)

    val WASHES = listOf(Wash(-0.035f, -0.035f), Wash(0.262f, -0.214f), Wash(-0.163f, 0.259f))

    data class HomeTile(val title: String, val colors: Colors, val symbol: Symbol, val wash: Wash, val orderOnly: Boolean = false)

    /** Home Collections tiles, in order (tokens.json `homeTiles`; People always shows a Buddha). */
    val homeTiles = listOf(
        HomeTile("Introducing Buddhism", Colors(0xFF979DFB, 0xFFFF42D3, 0xFF00ADEF), Symbol.WHEEL, WASHES[0]),
        HomeTile("Meditation & mindfulness", Colors(0xFF5ABA83, 0xFF00ADEF, 0xFF979DFB), Symbol.LOTUS, WASHES[1]),
        HomeTile("Latest talks & meditations", Colors(0xFFEC5E2B, 0xFFD13D7F, 0xFFEA1221), Symbol.VIHARA, WASHES[2]),
        HomeTile("Themes", Colors(0xFF00ADEF, 0xFF8B3DF6, 0xFFFF42D3), Symbol.WHEEL, WASHES[1]),
        HomeTile("Series", Colors(0xFF659CAE, 0xFF979DFB, 0xFF5ABA83), Symbol.LOTUS, WASHES[0]),
        HomeTile("People", Colors(0xFFEA1221, 0xFFFF42D3, 0xFFEC5E2B), Symbol.BUDDHA, WASHES[1]),
        HomeTile("Places", Colors(0xFF3E8C27, 0xFF659CAE, 0xFF5ABA83), Symbol.WHEEL, WASHES[2]),
        HomeTile("Order talks", Colors(0xFF006AA3, 0xFF00ADEF, 0xFF979DFB), Symbol.LOTUS, WASHES[0], orderOnly = true),
    )

    /** Rainbow sets for panels without a symbol, in order (red, orange, lime, ... magenta). */
    val rainbow = listOf(
        Colors(0xFFEA1221, 0xFFFF42D3, 0xFFFFC93C),
        Colors(0xFFEC5E2B, 0xFFFFC93C, 0xFFFF42D3),
        Colors(0xFF5ABA83, 0xFFFFC93C, 0xFFEC5E2B),
        Colors(0xFF5ABA83, 0xFF00ADEF, 0xFFFFC93C),
        Colors(0xFF659CAE, 0xFF5ABA83, 0xFFFFC93C),
        Colors(0xFF00ADEF, 0xFF979DFB, 0xFF5ABA83),
        Colors(0xFF979DFB, 0xFFFF42D3, 0xFF00ADEF),
        Colors(0xFF8B3DF6, 0xFFFF42D3, 0xFF979DFB),
        Colors(0xFFD13D7F, 0xFF8B3DF6, 0xFFFF42D3),
        Colors(0xFFFF42D3, 0xFFEC5E2B, 0xFFFFC93C),
    )

    /** For panels with a white symbol (Themes): the same order without yellow. */
    val symbolSafe = listOf(
        Colors(0xFFEA1221, 0xFFFF42D3, 0xFFEC5E2B),
        Colors(0xFFEC5E2B, 0xFFFF42D3, 0xFFEA1221),
        Colors(0xFF5ABA83, 0xFF00ADEF, 0xFF979DFB),
        Colors(0xFF659CAE, 0xFF5ABA83, 0xFF00ADEF),
        Colors(0xFFFF42D3, 0xFFEC5E2B, 0xFF979DFB),
    )

    /** Panels step through the rainbow from a random start, so neighbours never repeat. */
    fun rainbowAt(index: Int, start: Int): Colors = rainbow[Math.floorMod(start + index, rainbow.size)]
    fun symbolSafeAt(index: Int, start: Int): Colors = symbolSafe[Math.floorMod(start + index, symbolSafe.size)]
    fun washAt(index: Int): Wash = WASHES[Math.floorMod(index, WASHES.size)]

    const val YELLOW = 0xFFFFC93C
    const val SYMBOL_ALPHA = 0.22f
    const val SYMBOL_HEIGHT = 0.72f
    const val SYMBOL_CENTRE_X = 0.76f
    const val SYMBOL_CENTRE_Y = 0.44f
}
