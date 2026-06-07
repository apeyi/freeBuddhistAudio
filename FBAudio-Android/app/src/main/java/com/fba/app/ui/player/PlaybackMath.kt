package com.fba.app.ui.player

import com.fba.app.domain.model.Track

/**
 * Pure playback math, extracted from PlayerViewModel so it can be unit-tested
 * without the player/Media3 machinery. These cover the multi-track edge cases
 * (e.g. talks whose per-track durations are missing/zero) that previously
 * produced wrong progress like "0 of 0".
 */
object PlaybackMath {

    /**
     * Listened position across a whole multi-track talk: the summed duration of
     * all tracks before the current one, plus the position within the current
     * track. For single-track talks this is just the in-track position.
     */
    fun cumulativePositionMs(tracks: List<Track>, trackIndex: Int, positionInTrackMs: Long): Long {
        val safeIndex = trackIndex.coerceIn(0, maxOf(0, tracks.size))
        val priorMs = tracks.take(safeIndex).sumOf { it.durationSeconds.toLong() * 1000L }
        return priorMs + positionInTrackMs.coerceAtLeast(0)
    }

    /**
     * Best available total duration in **seconds**, in priority order:
     * 1. the talk's own duration metadata,
     * 2. the sum of per-track durations (if non-zero),
     * 3. the player-reported duration of the loaded item.
     *
     * The summed-tracks step is skipped when it totals zero, so talks whose
     * chapter metadata lacks durations still fall back to the real player
     * duration instead of reporting 0.
     */
    fun totalDurationSeconds(talkDurationSeconds: Int, tracks: List<Track>, playerDurationMs: Long): Int {
        if (talkDurationSeconds > 0) return talkDurationSeconds
        val summed = tracks.sumOf { it.durationSeconds }
        if (summed > 0) return summed
        return (playerDurationMs / 1000L).toInt().coerceAtLeast(0)
    }
}
