package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.ui.list.SeriesProgress
import com.dharmachakra.fba_android.ui.list.SeriesProgress.Saved
import com.dharmachakra.fba_android.ui.list.SeriesProgress.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SPEC "Series page, Resume". Parity with iOS SeriesProgressTests. */
class SeriesProgressTest {
    private val members = listOf("A", "B", "C", "D")
    private fun done(at: Long) = Saved(3540_000, 3600, at)        // within the last minute
    private fun partly(pos: Long, at: Long) = Saved(pos, 3600, at)

    @Test fun nothingHeardShowsPlayAll() {
        assertNull(SeriesProgress.resume(members, emptyMap()))
        assertNull(SeriesProgress.resume(members, mapOf("A" to partly(2_000, 1))))   // stray tap
    }

    @Test fun resumesTheTalkInProgress() {
        val r = SeriesProgress.resume(members, mapOf("A" to done(1), "B" to done(2), "C" to partly(1_740_000, 3)))!!
        assertEquals(2, r.index)
        assertEquals("C", r.catNum)
        assertEquals(1860, r.remainingSeconds)     // 31 min left
    }

    @Test fun afterAFinishedTalkResumesTheNextOne() {
        val r = SeriesProgress.resume(members, mapOf("A" to done(1), "B" to done(2)))!!
        assertEquals(2, r.index)
        assertNull(r.remainingSeconds)
    }

    @Test fun mostRecentPlayWins() {
        // Started D yesterday, went back to A today
        val r = SeriesProgress.resume(members, mapOf("D" to partly(600_000, 1), "A" to partly(300_000, 5)))!!
        assertEquals("A", r.catNum)
    }

    @Test fun everythingFinishedShowsPlayAll() {
        assertNull(SeriesProgress.resume(members, members.associateWith { done(1) }))
    }

    @Test fun states() {
        assertEquals(State.LISTENED, SeriesProgress.state(done(1)))
        assertEquals(State.IN_PROGRESS, SeriesProgress.state(partly(600_000, 1)))
        assertEquals(State.NOT_STARTED, SeriesProgress.state(null))
        assertEquals(State.NOT_STARTED, SeriesProgress.state(partly(1_000, 1)))
    }
}
