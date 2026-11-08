package com.dharmachakra.fba_android.ui.components

import kotlin.math.min
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

    /** A transparent or near-white pixel (ARGB): the background around a pre-cut circle. */
    fun isBlank(argb: Int): Boolean {
        val a = (argb ushr 24) and 0xFF
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return a <= 16 || (r >= 245 && g >= 245 && b >= 245)
    }

    /**
     * Whether an image is a pre-cut circle: blank (transparent or white, e.g. Kalyacitta's JPEG)
     * at all four corners and just inside them, with a non-blank centre. [pixel] reads (x, y).
     */
    fun looksPreCutCircle(width: Int, height: Int, pixel: (Int, Int) -> Int): Boolean {
        if (width < 16 || height < 16) return false
        val ins = (min(width, height) * 0.07).toInt().coerceAtLeast(2)
        val points = listOf(
            2 to 2, width - 3 to 2, 2 to height - 3, width - 3 to height - 3,
            ins to ins, width - 1 - ins to ins, ins to height - 1 - ins, width - 1 - ins to height - 1 - ins,
        )
        return points.all { (x, y) -> isBlank(pixel(x, y)) } && !isBlank(pixel(width / 2, height / 2))
    }

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
        // Floor, not round: rounding up can put the crop corners just outside the circle.
        val cw = w.toInt().coerceIn(1, width)
        val ch = h.toInt().coerceIn(1, height)
        return Crop((width - cw) / 2, (height - ch) / 2, cw, ch)
    }
}
