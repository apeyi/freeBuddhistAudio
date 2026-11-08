package com.dharmachakra.fba_android.ui.player

import com.dharmachakra.fba_android.ui.theme.AccentSet
import com.dharmachakra.fba_android.ui.theme.RemasterGold
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Full-screen player colour (SPEC "Player colour", 7 Oct): a top-to-bottom gradient between two
 * colours found in the artwork, the strongest at the top and a deeper one at the bottom, lightly
 * softened, with white text. Black-and-white artwork falls back to the hourly colour; remastered
 * talks are always gold. Pure ARGB maths so it is unit-tested; mirrors iOS `PlayerColors`.
 */
object PlayerColors {
    data class Gradient(val top: Int, val mid: Int, val bottom: Int)

    /** Below this saturation the artwork counts as black and white. */
    const val GREYSCALE_SATURATION = 0.2f
    /** White text needs dark enough backgrounds: caps on relative luminance. */
    const val MAX_TOP_LUMINANCE = 0.30
    const val MAX_BOTTOM_LUMINANCE = 0.12

    /**
     * From palette swatches (any may be missing). Null means "no usable colour" (black and white
     * or empty artwork): use [forAccent] instead.
     */
    fun fromSwatches(vibrant: Int?, darkVibrant: Int?, muted: Int?, darkMuted: Int?, dominant: Int?): Gradient? =
        fromColors(listOfNotNull(vibrant, darkVibrant, muted, darkMuted, dominant))

    /**
     * The top colour is the strongest one that suits white text: saturated, with light colours
     * (yellows) penalised because darkening them turns muddy. The bottom colour is a deeper one
     * of a clearly different hue when the artwork has it (the two-colour look in the design),
     * else the top colour deepened.
     */
    fun fromColors(colors: List<Int>): Gradient? {
        val saturated = colors.filter { saturation(it) >= GREYSCALE_SATURATION }
        val top = saturated.maxByOrNull { score(it) } ?: return null
        val bottom = saturated
            .filter { it != top && hueDistance(it, top) >= 40f && luminance(it) <= luminance(top) + 0.05 }
            .maxByOrNull { saturation(it) - luminance(it).toFloat() }
            ?: mix(top, BLACK, 0.5f)
        return finish(top, bottom)
    }

    private fun score(c: Int): Float = saturation(c) - (luminance(c).toFloat() - 0.25f).coerceAtLeast(0f) * 2f

    /** Hue in degrees, 0..360. */
    fun hue(c: Int): Float {
        val rf = r(c) / 255f; val gf = g(c) / 255f; val bf = b(c) / 255f
        val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf)); val d = mx - mn
        if (d == 0f) return 0f
        val h = when (mx) {
            rf -> ((gf - bf) / d) % 6f
            gf -> (bf - rf) / d + 2f
            else -> (rf - gf) / d + 4f
        } * 60f
        return if (h < 0) h + 360f else h
    }

    fun hueDistance(a: Int, b: Int): Float {
        val d = kotlin.math.abs(hue(a) - hue(b))
        return min(d, 360f - d)
    }

    /** The hourly colour (base on top into a deepened deep at the bottom). */
    fun forAccent(set: AccentSet): Gradient = finish(set.base.toInt(), mix(set.deep.toInt(), BLACK, 0.35f))

    /** Remastered talks: gold, with the top slightly deeper so white text reads well. */
    fun forGold(): Gradient = finish(RemasterGold.base.toInt(), RemasterGold.deep.toInt())

    /** Darken each end until white text is readable; the midpoint sits 45% down (Figma). */
    fun finish(top: Int, bottom: Int): Gradient {
        val t = darkenTo(top, MAX_TOP_LUMINANCE)
        val b = darkenTo(bottom, MAX_BOTTOM_LUMINANCE)
        return Gradient(t, mix(t, b, 0.45f), b)
    }

    // ---- colour maths (sRGB, ARGB ints) ----

    const val BLACK = 0xFF000000.toInt()

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    /** HSV-style saturation, 0..1. */
    fun saturation(c: Int): Float {
        val mx = max(r(c), max(g(c), b(c)))
        val mn = min(r(c), min(g(c), b(c)))
        return if (mx == 0) 0f else (mx - mn).toFloat() / mx
    }

    /** WCAG relative luminance, 0..1. */
    fun luminance(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(r(c)) + 0.7152 * ch(g(c)) + 0.0722 * ch(b(c))
    }

    /** Linear mix: 0 = [a], 1 = [b]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun m(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (m(r(a), r(b)) shl 16) or (m(g(a), g(b)) shl 8) or m(b(a), b(b))
    }

    fun darkenTo(c: Int, maxLuminance: Double): Int {
        var out = c
        var step = 0
        while (luminance(out) > maxLuminance && step < 40) {
            out = mix(out, BLACK, 0.06f)
            step++
        }
        return out
    }

    /** WCAG contrast ratio of white text on [c]. */
    fun contrastWithWhite(c: Int): Double = 1.05 / (luminance(c) + 0.05)
}
