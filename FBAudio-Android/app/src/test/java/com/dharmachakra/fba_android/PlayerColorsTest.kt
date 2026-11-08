package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.player.SleepFade
import com.dharmachakra.fba_android.player.SleepOption
import com.dharmachakra.fba_android.ui.player.PlayerColors
import com.dharmachakra.fba_android.ui.theme.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC full-screen player colour and sleep timer. Parity with iOS PlayerColorsTests. */
class PlayerColorsTest {
    private val orange = 0xFFE0592B.toInt()      // vibrant from LOC7132's banner
    private val navy = 0xFF264A6C.toInt()
    private val grey = 0xFF808080.toInt()

    @Test fun colourfulArtworkGivesStrongestOnTopDeeperBelow() {
        val g = PlayerColors.fromSwatches(vibrant = orange, darkVibrant = navy, muted = null, darkMuted = null, dominant = null)
        assertNotNull(g)
        assertTrue(PlayerColors.luminance(g!!.bottom) < PlayerColors.luminance(g.top))
        // Hue kept: the top is still clearly orange-red
        assertTrue((g.top shr 16 and 0xFF) > (g.top and 0xFF))
    }

    @Test fun blackAndWhiteArtworkFallsBackToTheHourlyColour() {
        assertNull(PlayerColors.fromSwatches(grey, 0xFF303030.toInt(), grey, null, grey))
        assertNull(PlayerColors.fromSwatches(null, null, null, null, null))
    }

    @Test fun whiteTextIsAlwaysReadable() {
        val cases = listOf(
            PlayerColors.fromSwatches(0xFFFFE680.toInt(), null, null, null, null)!!,   // pale yellow art
            PlayerColors.forAccent(Brand.FBA_BLUE.set),
            PlayerColors.forAccent(Brand.FBA_LILAC.set),
            PlayerColors.forGold(),
        )
        for (g in cases) {
            assertTrue("top ${Integer.toHexString(g.top)}", PlayerColors.contrastWithWhite(g.top) >= 3.0)
            assertTrue("bottom ${Integer.toHexString(g.bottom)}", PlayerColors.contrastWithWhite(g.bottom) >= 4.5)
        }
    }

    @Test fun prefersAStrongColourOverPaleYellowAndContrastsTheHue() {
        // LOC7132's banner: yellow, orange-red and blue swatches → orange-red into navy (Figma frame)
        val yellow = 0xFFE8C547.toInt(); val red = 0xFFC8452A.toInt(); val blue = 0xFF2F5B87.toInt()
        val g = PlayerColors.fromColors(listOf(yellow, red, blue))!!
        assertTrue("top should be red-orange", PlayerColors.hue(g.top) < 30f || PlayerColors.hue(g.top) > 340f)
        assertTrue("bottom should be blue", PlayerColors.hue(g.bottom) in 190f..250f)
    }

    @Test fun missingDeeperSwatchIsDerived() {
        val g = PlayerColors.fromSwatches(orange, null, null, null, null)!!
        assertTrue(PlayerColors.luminance(g.bottom) < PlayerColors.luminance(g.top))
    }

    @Test fun sleepFadeIsFullThenLinear() {
        assertEquals(1f, SleepFade.volume(60_000), 0f)
        assertEquals(1f, SleepFade.volume(10_000), 0f)
        assertEquals(0.5f, SleepFade.volume(5_000), 0.001f)
        assertEquals(0f, SleepFade.volume(0), 0f)
        assertEquals(0f, SleepFade.volume(-50), 0f)
    }

    @Test fun sleepOptionsMatchTheDesign() {
        assertEquals(
            listOf("Off", "5 minutes", "15 minutes", "30 minutes", "45 minutes", "1 hour", "End of this track", "End of this talk"),
            SleepOption.entries.map { it.label },
        )
        assertEquals("15 min", SleepOption.MIN_15.shortLabel)
        assertEquals("1 hr", SleepOption.HOUR_1.shortLabel)
    }
}
