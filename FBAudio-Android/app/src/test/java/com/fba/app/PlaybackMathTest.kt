package com.fba.app

import com.fba.app.domain.model.Track
import com.fba.app.ui.player.PlaybackMath
import org.junit.Assert.*
import org.junit.Test

class PlaybackMathTest {

    private fun track(seconds: Int) = Track(title = "", durationSeconds = seconds, audioUrl = "")

    // --- cumulativePositionMs ---

    @Test
    fun `cumulative position for single-track talk is just the in-track position`() {
        assertEquals(30_000L, PlaybackMath.cumulativePositionMs(emptyList(), 0, 30_000L))
    }

    @Test
    fun `cumulative position adds durations of prior tracks`() {
        val tracks = listOf(track(60), track(120), track(90))
        // On track index 2, 45s in: 60s + 120s prior + 45s = 225s
        assertEquals(225_000L, PlaybackMath.cumulativePositionMs(tracks, 2, 45_000L))
    }

    @Test
    fun `cumulative position clamps a negative position and out-of-range index`() {
        val tracks = listOf(track(60), track(60))
        assertEquals(120_000L, PlaybackMath.cumulativePositionMs(tracks, 99, -5L))
    }

    // --- totalDurationSeconds ---

    @Test
    fun `total duration prefers talk metadata`() {
        assertEquals(3600, PlaybackMath.totalDurationSeconds(3600, listOf(track(10)), 999_000L))
    }

    @Test
    fun `total duration falls back to summed tracks`() {
        val tracks = listOf(track(60), track(120))
        assertEquals(180, PlaybackMath.totalDurationSeconds(0, tracks, 999_000L))
    }

    @Test
    fun `total duration falls back to player duration when tracks have no durations`() {
        // The "Jewel in the Lotus" case: 16 chapters, all durationSeconds == 0.
        val tracks = List(16) { track(0) }
        assertEquals(95, PlaybackMath.totalDurationSeconds(0, tracks, 95_000L))
    }

    @Test
    fun `total duration is zero only when nothing is known`() {
        assertEquals(0, PlaybackMath.totalDurationSeconds(0, emptyList(), 0L))
    }

    @Test
    fun clampPositionKeepsTimeWithinNewVersion() {
        assertEquals(120_000L, PlaybackMath.clampPosition(120_000L, 760_000L))
        // Remastered track is 9 s shorter than where we were → land just before its end
        assertEquals(759_000L, PlaybackMath.clampPosition(768_000L, 760_000L))
        assertEquals(50_000L, PlaybackMath.clampPosition(50_000L, null))
        assertEquals(50_000L, PlaybackMath.clampPosition(50_000L, 0L))
        assertEquals(0L, PlaybackMath.clampPosition(-5L, 10_000L))
    }

    @Test
    fun absurdTalkDurationFallsBackToTracks() {
        val tracks = listOf(Track("", 1200, ""), Track("", 1800, ""))
        // LOC3883 on the website: 717,860,544 seconds
        assertEquals(3000, PlaybackMath.totalDurationSeconds(717_860_544, tracks, 0))
        assertEquals(0, PlaybackMath.totalDurationSeconds(717_860_544, emptyList(), 0))
        assertEquals(42, PlaybackMath.totalDurationSeconds(-5, emptyList(), 42_000))
        assertTrue(PlaybackMath.isPlausibleDuration(20 * 3600))
        assertFalse(PlaybackMath.isPlausibleDuration(0))
    }

    @Test
    fun estimateDurationFromSiblingBitrate() {
        // LOC3883: chapter 4 is 27,933,382 bytes for 3491 s (64 kbps); chapter 5 has no duration on the site
        assertEquals(4229, PlaybackMath.estimateDurationSeconds(33_842_698L, 27_933_382L, 3491))
        assertEquals(0, PlaybackMath.estimateDurationSeconds(0L, 27_933_382L, 3491))
        assertEquals(0, PlaybackMath.estimateDurationSeconds(1_000L, 0L, 3491))
        assertEquals(0, PlaybackMath.estimateDurationSeconds(1_000L, 27_933_382L, 0))
    }
}
