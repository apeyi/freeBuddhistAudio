package com.fba.app

import com.fba.app.domain.FuzzyMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchTest {

    @Test
    fun substringAlwaysMatches() {
        assertTrue(FuzzyMatch.matches("lbc", "talks from east london (lbc)"))
        assertTrue(FuzzyMatch.matches("buddha", "Buddhafield"))
        assertTrue(FuzzyMatch.matches("Subhuti", "subhuti"))
    }

    @Test
    fun toleratesTyposScaledByLength() {
        assertTrue(FuzzyMatch.matches("adhistana", "Adhisthana"))      // missing h
        assertTrue(FuzzyMatch.matches("sanghrakshita", "Sangharakshita")) // missing a
        assertTrue(FuzzyMatch.matches("padmaloka", "Padmaloka Retreat Centre"))
        assertTrue(FuzzyMatch.matches("taraloka", "Taraloka Retreat Centre"))
        assertTrue(FuzzyMatch.matches("vesantara", "Vessantara"))
        assertFalse(FuzzyMatch.matches("oxf", "Oakford"))              // too short for fuzziness
    }

    @Test
    fun shortQueriesAreExactOnly() {
        assertEquals(0, FuzzyMatch.allowedEdits(4))
        assertEquals(1, FuzzyMatch.allowedEdits(5))
        assertEquals(2, FuzzyMatch.allowedEdits(8))
        assertFalse(FuzzyMatch.matches("bxdh", "Bodh Gaya"))
    }

    @Test
    fun rejectsUnrelatedNames() {
        assertFalse(FuzzyMatch.matches("adhisthana", "Aryaloka Buddhist Center"))
        assertFalse(FuzzyMatch.matches("valencia", "Vajrasana Retreat Centre"))
        assertFalse(FuzzyMatch.matches("manchester", "Cambridge"))
    }

    @Test
    fun ignoresDiacriticsAndCase() {
        assertTrue(FuzzyMatch.matches("sao paulo", "São Paulo"))
        assertTrue(FuzzyMatch.matches("centro budista satelite", "Centro Budista Satélite"))
        assertEquals(1, FuzzyMatch.distance("adhistana", "adhisthana"))
        assertEquals(1, FuzzyMatch.distance("teh", "the")) // transposition
    }
}
