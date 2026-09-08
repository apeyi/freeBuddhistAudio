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
        if (isPlausibleDuration(talkDurationSeconds)) return talkDurationSeconds
        val summed = tracks.sumOf { it.durationSeconds.coerceAtLeast(0) }
        if (isPlausibleDuration(summed)) return summed
        return (playerDurationMs / 1000L).toInt().coerceAtLeast(0)
    }

    /**
     * The website's duration field is sometimes garbage (e.g. 717,860,544 s ≈ 22
     * years for LOC3883). Anything longer than the longest audiobook on the site
     * is treated as missing and derived from the tracks / player instead.
     */
    const val MAX_PLAUSIBLE_SECONDS = 100 * 3600

    fun isPlausibleDuration(seconds: Int): Boolean = seconds in 1..MAX_PLAUSIBLE_SECONDS

    /**
     * Position to resume at after switching between the remastered and original
     * recording: the same absolute time, clamped to the new track's duration
     * (the two versions differ by a few seconds). Unknown duration → unchanged.
     */
    fun clampPosition(positionMs: Long, newDurationMs: Long?): Long {
        val pos = positionMs.coerceAtLeast(0)
        if (newDurationMs == null || newDurationMs <= 0) return pos
        return pos.coerceAtMost((newDurationMs - 1000).coerceAtLeast(0))
    }

    /**
     * Length of a chapter whose duration the website lacks, from its file size and
     * a sibling chapter with a known length (bitrate is constant within a talk but
     * varies between talks — 64 kbps originals, 256 kbps remasters). 0 when the
     * inputs can't support an estimate.
     */
    fun estimateDurationSeconds(bytes: Long, refBytes: Long, refSeconds: Int): Int {
        if (bytes <= 0 || refBytes <= 0 || refSeconds <= 0) return 0
        val bytesPerSecond = refBytes.toDouble() / refSeconds
        val estimate = (bytes / bytesPerSecond).toInt()
        return if (isPlausibleDuration(estimate)) estimate else 0
    }
}
