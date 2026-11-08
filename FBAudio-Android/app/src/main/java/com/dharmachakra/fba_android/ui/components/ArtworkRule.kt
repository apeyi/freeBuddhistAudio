package com.dharmachakra.fba_android.ui.components

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The design's talk artwork rule (SPEC "Talk artwork: which image to show"), pure so it can be
 * unit-tested. Always shown as a rounded rectangle, never a circle.
 */
object ArtworkRule {
    /**
     * Which image to show for a talk: the banner, else the speaker image, else the talk's own
     * list image; null means the "Various" Standing Buddha fallback. Blank URLs and the site's
     * generic "/default" placeholders count as missing.
     */
    fun talkImage(bannerUrl: String?, speakerImageUrl: String?, listImageUrl: String?): String? =
        listOf(bannerUrl, speakerImageUrl, listImageUrl).firstOrNull { usable(it) }

    fun usable(url: String?): Boolean = !url.isNullOrBlank() && !url.contains("/default")

    /** A crop rectangle in source pixels. */
    data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * Some speaker images are stored pre-cut into a circle (transparent corners). Crop the
     * largest rectangle of the target [aspect] (width / height) that fits inside that circle,
     * so no corner or circle edge shows and the face keeps its proportions.
     */
    fun inscribedCrop(width: Int, height: Int, aspect: Float): Crop {
        val diameter = min(width, height).toDouble()
        val h = diameter / sqrt(1.0 + aspect * aspect)
        val w = h * aspect
        val cw = w.roundToInt().coerceIn(1, width)
        val ch = h.roundToInt().coerceIn(1, height)
        return Crop((width - cw) / 2, (height - ch) / 2, cw, ch)
    }
}
