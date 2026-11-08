package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.ui.components.ArtworkRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC "Talk artwork: which image to show". Parity with iOS ArtworkRuleTests. */
class ArtworkRuleTest {
    @Test fun bannerThenSpeakerThenListImageThenVarious() {
        assertEquals("b", ArtworkRule.talkImage("b", "s", "l"))
        assertEquals("s", ArtworkRule.talkImage("", "s", "l"))
        assertEquals("s", ArtworkRule.talkImage(null, "s", "l"))
        assertEquals("l", ArtworkRule.talkImage(" ", null, "l"))
        assertNull(ArtworkRule.talkImage(null, "", null))          // → "Various" image
    }

    @Test fun sitePlaceholdersCountAsMissing() {
        assertEquals("s", ArtworkRule.talkImage("https://x/images/default.jpg", "s", null))
    }

    @Test fun inscribedCropFitsInsideTheCircle() {
        // 500 x 500 circular PNG (e.g. Jnanalila), wide artwork 362 x 240
        val c = ArtworkRule.inscribedCrop(500, 500, 362f / 240f)
        assertEquals(417, c.width)
        assertEquals(276, c.height)
        assertEquals((500 - 417) / 2, c.left)
        assertEquals((500 - 276) / 2, c.top)
        // corners of the crop lie inside the circle (radius 250 around the centre)
        val dx = c.width / 2.0; val dy = c.height / 2.0
        assertTrue(dx * dx + dy * dy <= 250.0 * 250.0 + 1)
        // square thumbnail: side = d / sqrt(2)
        assertEquals(354, ArtworkRule.inscribedCrop(500, 500, 1f).width)
    }
}
