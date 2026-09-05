package com.fba.app

import com.fba.app.domain.LanguageFilter
import com.fba.app.domain.model.MenuNode
import com.fba.app.domain.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageFilterTest {

    private val menu = listOf(
        MenuNode("people", "#", children = listOf(
            MenuNode("abhaya", "/browse?s=Abhaya"),
            MenuNode("amalamati (en español)", "/browse?s=Amalamati"),
            MenuNode("dharmadipa (deutsch | english)", "/browse?s=Dharmadipa"),
            MenuNode("chandrabodhi (हिंदी में)", "/browse?s=Chandrabodhi"),
        )),
        MenuNode("places", "#", children = listOf(
            MenuNode("barcelona (españa)", "/browse?p=Triratna_Barcelona"),
            MenuNode("cambridge (england)", "/browse?p=Cambridge"),
            MenuNode("nagpur (भारत | india)", "/browse?p=Nagpur"),
            MenuNode("london (uk)", "#", children = listOf(MenuNode("talks from north london", "/browse?p=North_London"))),
        )),
        MenuNode("languages", "#languages", children = listOf(
            MenuNode("лекции на русском языке", "/browse?s=Suvannavira"),
            MenuNode("forelesninger på norsk", "/browse?p=Oslo"),
        )),
    )

    @Test
    fun labelMarkersDetectNonEnglishButNotBilingual() {
        assertTrue(LanguageFilter.isNonEnglishLabel("amalamati (en español)"))
        assertTrue(LanguageFilter.isNonEnglishLabel("Bodhisattva Ideal (deutsch)"))
        assertTrue(LanguageFilter.isNonEnglishLabel("chandrabodhi (हिंदी में)"))
        assertFalse(LanguageFilter.isNonEnglishLabel("dharmadipa (deutsch | english)"))
        assertFalse(LanguageFilter.isNonEnglishLabel("abhaya"))
        assertFalse(LanguageFilter.isNonEnglishLabel("The Buddha's Noble Eightfold Path"))
    }

    @Test
    fun placeLabelsDetectNonEnglishCountriesButLeaveIndiaVisible() {
        assertTrue(LanguageFilter.isNonEnglishPlaceLabel("barcelona (españa)"))
        assertTrue(LanguageFilter.isNonEnglishPlaceLabel("essen (deutschland)"))
        assertFalse(LanguageFilter.isNonEnglishPlaceLabel("nagpur (भारत | india)"))
        assertFalse(LanguageFilter.isNonEnglishPlaceLabel("cambridge (england)"))
    }

    @Test
    fun speakersAndCentresAreDerivedFromTheMenu() {
        assertEquals(setOf("amalamati", "chandrabodhi", "suvannavira"), LanguageFilter.nonEnglishSpeakers(menu))
        val centres = LanguageFilter.nonEnglishCentres(menu)
        assertTrue("triratna barcelona" in centres)
        assertTrue("barcelona" in centres)
        assertTrue("oslo" in centres)
        assertFalse("cambridge" in centres)
        assertFalse("nagpur" in centres)
    }

    @Test
    fun filterMenuHidesMarkedEntriesRecursively() {
        val filtered = LanguageFilter.filterMenu(menu[0].children, englishOnly = true)
        assertEquals(listOf("abhaya", "dharmadipa (deutsch | english)"), filtered.map { it.label })
        assertEquals(4, LanguageFilter.filterMenu(menu[0].children, englishOnly = false).size)
    }

    @Test
    fun filterItemsUsesSpeakerCentreAndTitleMarkers() {
        val items = listOf(
            SearchResult("1", "The Revealer of Treasures", "Vajrashura", "", "/audio/details?num=1", centre = "Dublin"),
            SearchResult("2", "El mantra de Padmasambhava", "Amalamati", "", "/audio/details?num=2", centre = "Valencia"),
            SearchResult("3", "Nacido del loto", "Silamani", "", "/audio/details?num=3", centre = "Triratna Barcelona"),
            SearchResult("4", "Achtsamkeit (deutsch)", "Someone", "", "/audio/details?num=4"),
        )
        val speakers = LanguageFilter.nonEnglishSpeakers(menu)
        val centres = LanguageFilter.nonEnglishCentres(menu)
        val kept = LanguageFilter.filterItems(items, true, speakers, centres)
        assertEquals(listOf("1"), kept.map { it.catNum })
        assertEquals(4, LanguageFilter.filterItems(items, false, speakers, centres).size)
    }

    @Test
    fun linkHelpersDecodeNames() {
        assertEquals("vajragupta (m)", LanguageFilter.speakerFromLink("/browse?s=Vajragupta_(m)&t=audio"))
        assertEquals("triratna barcelona", LanguageFilter.placeFromLink("/browse?p=Triratna_Barcelona"))
        assertEquals(null, LanguageFilter.speakerFromLink("/collection/subhuti"))
    }
}
