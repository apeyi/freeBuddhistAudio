package com.fba.app

import com.fba.app.domain.CollectionArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionArtworkTest {

    @Test
    fun hueIsStableAndInRange() {
        val slugs = listOf("buddha", "meditation-mindfulness", "bodhisattva-ideal", "tara", "arts", "wisdom")
        for (slug in slugs) {
            val h = CollectionArtwork.hue(slug)
            assertEquals(h, CollectionArtwork.hue(slug), 0f)
            assertTrue("$slug → $h", h >= 0f && h < 360f)
        }
    }

    @Test
    fun similarSlugsGetDifferentHues() {
        assertNotEquals(CollectionArtwork.hue("tara"), CollectionArtwork.hue("arts"), 0.5f)
        assertNotEquals(CollectionArtwork.hue("ethics"), CollectionArtwork.hue("wisdom"), 0.5f)
    }

    @Test
    fun caseInsensitiveAndEmptySafe() {
        assertEquals(CollectionArtwork.hue("Buddha"), CollectionArtwork.hue("buddha"), 0f)
        assertEquals(0f, CollectionArtwork.hue(""), 0f)
        assertEquals((CollectionArtwork.hue("buddha") + 40f) % 360f, CollectionArtwork.secondHue("buddha"), 0f)
    }
}
