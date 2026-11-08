package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.data.local.RecentlyListenedEntity
import com.dharmachakra.fba_android.ui.myfba.MyFbaRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC "My FBA" and "Continue listening (See all)". Parity with iOS MyFbaRulesTests. */
class MyFbaRulesTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun entry(cat: String, posSec: Int, total: Int = 3600, daysAgo: Long = 0, track: Int = 0, speaker: String = "Sangharakshita") =
        RecentlyListenedEntity(
            catNum = cat, title = cat, speaker = speaker, imageUrl = "",
            positionMs = posSec * 1000L, trackIndex = track, totalDurationSeconds = total,
            listenedAt = now - daysAgo * day,
        )

    @Test fun inProgressNeedsAStartAndTimeLeft() {
        assertFalse(MyFbaRules.isInProgress(entry("a", 2), now)) // stray tap
        assertTrue(MyFbaRules.isInProgress(entry("a", 600), now))
        assertFalse(MyFbaRules.isInProgress(entry("a", 3550), now)) // within the last minute
        assertTrue(MyFbaRules.isInProgress(entry("a", 600, total = 0), now)) // length unknown
    }

    @Test fun leavesAfter90DaysWithoutAPlay() {
        assertTrue(MyFbaRules.isInProgress(entry("a", 600, daysAgo = 89), now))
        assertFalse(MyFbaRules.isInProgress(entry("a", 600, daysAgo = 91), now))
    }

    @Test fun continueListeningIsMostRecentFirst() {
        val list = listOf(entry("old", 600, daysAgo = 5), entry("done", 3599), entry("new", 600, daysAgo = 1))
        assertEquals(listOf("new", "old"), MyFbaRules.continueListening(list, now).map { it.catNum })
    }

    @Test fun finished() {
        assertTrue(MyFbaRules.isFinished(entry("a", 3550)))
        assertFalse(MyFbaRules.isFinished(entry("a", 600)))
        assertFalse(MyFbaRules.isFinished(entry("a", 600, total = 0)))
    }

    @Test fun meta() {
        assertEquals("Sangharakshita · 50 min left", MyFbaRules.continueMeta(entry("a", 600)))
        assertEquals("Sangharakshita · Track 3 · 50 min left", MyFbaRules.continueMeta(entry("a", 600, track = 2)))
        assertEquals("50 min left", MyFbaRules.continueMeta(entry("a", 600, speaker = "")))
    }

    @Test fun labels() {
        assertEquals("1 talk in progress", MyFbaRules.inProgressLabel(1))
        assertEquals("7 talks in progress, most recent first", MyFbaRules.inProgressLabel(7))
        assertEquals("None yet", MyFbaRules.downloadsSummary(0, 0))
        assertEquals("1 talk · 64 MB", MyFbaRules.downloadsSummary(1, 64_400_000))
        assertEquals("12 talks · 1.2 GB", MyFbaRules.downloadsSummary(12, 1_234_000_000))
        assertEquals("80 KB", MyFbaRules.formatBytes(80_000))
    }

    @Test fun progress() {
        assertEquals(0.5f, MyFbaRules.progress(entry("a", 1800)), 0.001f)
        assertEquals(0f, MyFbaRules.progress(entry("a", 1800, total = 0)), 0f)
    }

    @Test fun historyDayLabels() {
        val utc = java.time.ZoneId.of("UTC")
        // now = 2027-01-15T08:00:00Z (a Friday)
        val n = java.time.Instant.parse("2027-01-15T08:00:00Z").toEpochMilli()
        fun at(s: String) = java.time.Instant.parse(s).toEpochMilli()
        assertEquals("Today", MyFbaRules.historyDayLabel(at("2027-01-15T00:10:00Z"), n, utc))
        assertEquals("Yesterday", MyFbaRules.historyDayLabel(at("2027-01-14T23:59:00Z"), n, utc))
        assertEquals("Monday 11 January", MyFbaRules.historyDayLabel(at("2027-01-11T12:00:00Z"), n, utc))
        assertEquals("3 November 2026", MyFbaRules.historyDayLabel(at("2026-11-03T12:00:00Z"), n, utc))
    }
}
