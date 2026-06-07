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
}
