package com.fba.app

import com.fba.app.domain.model.SangharakshitaData
import org.junit.Assert.*
import org.junit.Test

class SangharakshitaDataTest {

    @Test
    fun `talks are loaded`() {
        val talks = SangharakshitaData.allTalksAsSearchResults()
        assertTrue("Should have 300+ talks, got ${talks.size}", talks.size > 300)
    }

    @Test
    fun `series are loaded`() {
        val series = SangharakshitaData.series
        assertTrue("Should have 20+ series, got ${series.size}", series.size > 20)
    }

    @Test
    fun `titles are fixed`() {
        val talks = SangharakshitaData.allTalksAsSearchResults()
        for (talk in talks) {
            assertFalse("Title not fixed: ${talk.title}", talk.title.endsWith(", The"))
            assertFalse("Title not fixed: ${talk.title}", talk.title.endsWith(", A"))
            assertFalse("Title not fixed: ${talk.title}", talk.title.endsWith(", An"))
        }
    }

    @Test
    fun `all talks have catNum`() {
        val talks = SangharakshitaData.allTalksAsSearchResults()
        assertTrue(talks.all { it.catNum.isNotBlank() })
    }

    @Test
    fun `all talks have speaker set to Sangharakshita`() {
        val talks = SangharakshitaData.allTalksAsSearchResults()
        assertTrue(talks.all { it.speaker == "Sangharakshita" })
    }

    @Test
    fun `series browse categories have correct type and url`() {
        val categories = SangharakshitaData.seriesAsBrowseCategories()
        assertTrue(categories.isNotEmpty())
        for (cat in categories) {
            assertTrue(cat.browseUrl.contains("/series/details"))
            assertEquals(com.fba.app.domain.model.CategoryType.SERIES, cat.type)
        }
    }
}
