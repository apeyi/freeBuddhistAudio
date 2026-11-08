package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.ui.home.FeaturedLink
import com.dharmachakra.fba_android.ui.home.FeaturedTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class FeaturedLinkTest {
    @Test fun routesTheSitesLinks() {
        assertEquals(FeaturedTarget.Series("X01"), FeaturedLink.target("https://www.freebuddhistaudio.com/series/details?num=X01"))
        assertEquals(FeaturedTarget.Talk("LOC6479"), FeaturedLink.target("https://www.freebuddhistaudio.com/audio/details?num=LOC6479"))
        assertEquals(FeaturedTarget.DigitalLegacy, FeaturedLink.target("https://www.freebuddhistaudio.com/digital-legacy"))
        assertEquals(FeaturedTarget.Web("https://www.freebuddhistaudio.com/about"), FeaturedLink.target("https://www.freebuddhistaudio.com/about"))
    }

    @Test fun bandsCycle() {
        assertEquals(FeaturedLink.BANDS[0], FeaturedLink.band(FeaturedLink.BANDS.size))
    }
}
