package com.dharmachakra.fba_android.ui.detail

import com.dharmachakra.fba_android.domain.model.Track
import com.dharmachakra.fba_android.ui.player.PlaybackMath

/**
 * Resume and listening-progress rules for talk pages (SPEC "Talk page, Resume (partly
 * listened)"), pure so they're unit-tested. Mirrors iOS `TalkProgress`.
 */
object TalkProgress {
    /** A talk counts as listened once played to within about the last minute. */
    const val FINISHED_WITHIN_SECONDS = 60

    /** Below this, a first-track position is a stray tap, not a talk in progress. */
    const val STARTED_AFTER_MS = 5_000L

    data class Resume(val trackIndex: Int, val positionMs: Long, val remainingSeconds: Int)

    enum class TrackState { NOT_STARTED, LISTENED, IN_PROGRESS }

    /**
     * Where to resume, or null to show plain "Play" (never started, or finished).
     * [savedTrack]/[savedPositionMs] is the saved point (the account checkpoint already
     * applied by the caller); [totalSeconds] is the talk's best-known length.
     */
    fun resume(tracks: List<Track>, totalSeconds: Int, savedTrack: Int, savedPositionMs: Long): Resume? {
        if (savedTrack <= 0 && savedPositionMs < STARTED_AFTER_MS) return null
        val index = savedTrack.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        val position = savedPositionMs.coerceAtLeast(0)
        val remaining = PlaybackMath.remainingSeconds(totalSeconds, tracks, index, position)
        if (totalSeconds > 0 && remaining <= FINISHED_WITHIN_SECONDS) return null
        return Resume(index, position, remaining)
    }

    fun trackState(index: Int, resume: Resume?): TrackState = when {
        resume == null -> TrackState.NOT_STARTED
        index < resume.trackIndex -> TrackState.LISTENED
        index == resume.trackIndex -> TrackState.IN_PROGRESS
        else -> TrackState.NOT_STARTED
    }

    /** How far through its own track the in-progress track is, 0..1 (0 when unknown). */
    fun trackFraction(track: Track, positionMs: Long): Float {
        if (track.durationSeconds <= 0) return 0f
        return (positionMs / 1000f / track.durationSeconds).coerceIn(0f, 1f)
    }

    /** Seconds left in one track. */
    fun trackRemainingSeconds(track: Track, positionMs: Long): Int =
        (track.durationSeconds - (positionMs / 1000L).toInt()).coerceAtLeast(0)

    /** Line under the Resume button: "Track 2 · 18 min left", or "18 min left" for one track. */
    fun resumeLabel(resume: Resume, trackCount: Int): String {
        val left = PlaybackMath.timeLeftLabel(resume.remainingSeconds)
        val track = if (trackCount > 1) "Track ${resume.trackIndex + 1}" else null
        return listOfNotNull(track, left).joinToString(" · ")
    }
}
