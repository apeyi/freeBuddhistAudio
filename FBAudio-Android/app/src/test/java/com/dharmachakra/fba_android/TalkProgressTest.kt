package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.domain.model.Track
import com.dharmachakra.fba_android.ui.detail.TalkProgress
import com.dharmachakra.fba_android.ui.detail.TalkProgress.TrackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SPEC "Talk page, Resume (partly listened)". Parity with iOS TalkProgressTests. */
class TalkProgressTest {
    private fun track(seconds: Int) = Track(title = "", durationSeconds = seconds, audioUrl = "")
    // LOC7132: 1:00:49 + 29:06
    private val tracks = listOf(track(3649), track(1746))
    private val total = 5395

    @Test fun neverStartedShowsPlay() {
        assertNull(TalkProgress.resume(tracks, total, 0, 0))
        assertNull(TalkProgress.resume(tracks, total, 0, 3_000))   // a stray tap
    }

    @Test fun partlyHeardResumes() {
        // Track 1 finished, 11:02 into track 2 → 19 min left (rounded up)
        val r = TalkProgress.resume(tracks, total, 1, 662_000)!!
        assertEquals(1, r.trackIndex)
        assertEquals(1084, r.remainingSeconds)
        assertEquals("Track 2 · 19 min left", TalkProgress.resumeLabel(r, tracks.size))
        assertEquals("19 min left", TalkProgress.resumeLabel(r, 1))
    }

    @Test fun withinTheLastMinuteCountsAsListened() {
        assertNull(TalkProgress.resume(tracks, total, 1, (1746 - 30) * 1000L))
        // ...but a minute and a half out still resumes
        assertEquals(90, TalkProgress.resume(tracks, total, 1, (1746 - 90) * 1000L)!!.remainingSeconds)
    }

    @Test fun trackStates() {
        val r = TalkProgress.resume(tracks, total, 1, 662_000)
        assertEquals(TrackState.LISTENED, TalkProgress.trackState(0, r))
        assertEquals(TrackState.IN_PROGRESS, TalkProgress.trackState(1, r))
        assertEquals(TrackState.NOT_STARTED, TalkProgress.trackState(2, r))
        assertEquals(TrackState.NOT_STARTED, TalkProgress.trackState(0, null))
    }

    @Test fun trackFractionAndRemaining() {
        assertEquals(0.5f, TalkProgress.trackFraction(track(600), 300_000), 0.0001f)
        assertEquals(0f, TalkProgress.trackFraction(track(0), 300_000), 0.0001f)
        assertEquals(1f, TalkProgress.trackFraction(track(600), 900_000), 0.0001f)
        assertEquals(1084, TalkProgress.trackRemainingSeconds(track(1746), 662_000))
    }

    @Test fun outOfRangeSavedTrackIsClamped() {
        assertEquals(1, TalkProgress.resume(tracks, total, 7, 10_000)!!.trackIndex)
    }

    @Test fun lengthLabelMatchesTheDesign() {
        assertEquals("59 min", com.dharmachakra.fba_android.ui.detail.lengthLabel(59 * 60))
        assertEquals("90 min", com.dharmachakra.fba_android.ui.detail.lengthLabel(5395))
        assertEquals("2 hr", com.dharmachakra.fba_android.ui.detail.lengthLabel(7200))
        assertEquals("8 hr 14 min", com.dharmachakra.fba_android.ui.detail.lengthLabel(8 * 3600 + 14 * 60))
    }
}
