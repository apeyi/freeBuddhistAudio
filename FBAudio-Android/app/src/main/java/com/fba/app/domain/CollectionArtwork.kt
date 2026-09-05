package com.fba.app.domain

/**
 * Auto-generated artwork for collections without a cover image: a two-stop
 * gradient whose hues are derived from the collection's slug, so the same
 * collection always looks the same (cf. Apple Music genre tiles).
 */
object CollectionArtwork {

    /** Stable hue in [0, 360) for a slug. Empty slug → 0. */
    fun hue(slug: String): Float {
        if (slug.isBlank()) return 0f
        // FNV-1a keeps distribution even for short, similar slugs ("tara", "arts").
        var h = 0x811C9DC5.toInt()
        for (c in slug.lowercase()) {
            h = h xor c.code
            h *= 0x01000193
        }
        val positive = (h.toLong() and 0xFFFFFFFFL)
        return (positive % 360L).toFloat()
    }

    /** Second gradient stop: 40° around the wheel so the gradient reads as one colour family. */
    fun secondHue(slug: String): Float = (hue(slug) + 40f) % 360f
}
