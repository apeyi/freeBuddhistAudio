package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.ui.components.TileArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC "Tile artwork". Parity with iOS TileArtTests. */
class TileArtTest {
    @Test fun homeTilesInDesignOrderPeopleIsABuddha() {
        assertEquals(
            listOf("Introducing Buddhism", "Meditation & mindfulness", "Latest talks & meditations", "Themes",
                "Series", "People", "Places", "Order talks"),
            TileArt.homeTiles.map { it.title },
        )
        assertEquals(TileArt.Symbol.BUDDHA, TileArt.homeTiles.first { it.title == "People" }.symbol)
        assertTrue(TileArt.homeTiles.single { it.orderOnly }.title == "Order talks")
    }

    @Test fun mainNeverBlendsIntoTheSameColour() {
        (TileArt.homeTiles.map { it.colors } + TileArt.rainbow + TileArt.symbolSafe).forEach {
            assertNotEquals(it.main, it.partner)
        }
    }

    @Test fun yellowIsNeverTheMainColourAndNeverNextToASymbol() {
        TileArt.rainbow.forEach { assertNotEquals(TileArt.YELLOW, it.main) }
        (TileArt.symbolSafe + TileArt.homeTiles.map { it.colors }).forEach {
            assertTrue(TileArt.YELLOW !in listOf(it.main, it.partner, it.glow))
        }
    }

    @Test fun rainbowStepsFromTheStartAndWraps() {
        assertEquals(TileArt.rainbow[3], TileArt.rainbowAt(0, 3))
        assertEquals(TileArt.rainbow[4], TileArt.rainbowAt(1, 3))
        assertEquals(TileArt.rainbow[0], TileArt.rainbowAt(7, 3))
        // neighbours differ
        (0 until 20).forEach { assertNotEquals(TileArt.rainbowAt(it, 5), TileArt.rainbowAt(it + 1, 5)) }
    }
}
