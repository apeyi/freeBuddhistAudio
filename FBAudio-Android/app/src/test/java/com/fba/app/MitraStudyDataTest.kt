package com.fba.app

import com.fba.app.domain.model.MitraStudyData
import org.junit.Assert.*
import org.junit.Test

class MitraStudyDataTest {

    @Test
    fun `modules are loaded`() {
        val modules = MitraStudyData.modules
        assertTrue("Should have 20+ modules, got ${modules.size}", modules.size > 20)
    }

    @Test
    fun `modules grouped by year covers years 1 to 4`() {
        val grouped = MitraStudyData.modulesByYear()
        assertEquals(setOf(1, 2, 3, 4), grouped.keys)
        for ((_, modules) in grouped) {
            assertTrue(modules.isNotEmpty())
        }
    }

    @Test
    fun `module talks can be loaded as search results`() {
        val talks = MitraStudyData.moduleTalksAsSearchResults("y1_refuge")
        assertTrue("y1_refuge should have talks, got ${talks.size}", talks.isNotEmpty())
        assertTrue(talks.all { it.catNum.isNotBlank() })
    }

    @Test
    fun `unknown module returns empty list`() {
        val talks = MitraStudyData.moduleTalksAsSearchResults("nonexistent")
        assertTrue(talks.isEmpty())
    }

    @Test
    fun `year categories returns 4 years`() {
        val categories = MitraStudyData.yearCategories()
        assertEquals(4, categories.size)
        assertEquals(listOf("Year 1", "Year 2", "Year 3", "Year 4"), categories.map { it.name })
    }

    @Test
    fun `module categories for year 1`() {
        val categories = MitraStudyData.moduleCategories(1)
        assertTrue("Year 1 should have modules", categories.isNotEmpty())
        assertTrue(categories.all { it.type == com.fba.app.domain.model.CategoryType.MITRA_MODULE })
    }

    @Test
    fun `all modules have non-blank id and name`() {
        for (module in MitraStudyData.modules) {
            assertTrue("Module id blank", module.id.isNotBlank())
            assertTrue("Module name blank", module.name.isNotBlank())
        }
    }
}
