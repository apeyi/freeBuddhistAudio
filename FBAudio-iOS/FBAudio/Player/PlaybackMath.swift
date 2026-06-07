import Foundation

/// Pure playback math, extracted from AudioPlayer so it can be unit-tested
/// without AVFoundation. Mirrors the Android `PlaybackMath`. Covers the
/// multi-track edge cases (e.g. talks whose per-track durations are
/// missing/zero) that previously produced wrong progress like "0 of 0".
enum PlaybackMath {

    /// Listened position across a whole multi-track talk: the summed duration
    /// of all tracks before the current one, plus the position within the
    /// current track. For single-track talks this is just the in-track position.
    static func cumulativePositionMs(tracks: [Track], trackIndex: Int, positionInTrackMs: Int64) -> Int64 {
        let safeIndex = max(0, min(trackIndex, tracks.count))
        let priorMs = tracks.prefix(safeIndex).reduce(Int64(0)) { $0 + Int64($1.durationSeconds) * 1000 }
        return priorMs + max(0, positionInTrackMs)
    }

    /// Best available total duration in **seconds**, in priority order:
    /// 1. the talk's own duration metadata,
    /// 2. the sum of per-track durations (if non-zero),
    /// 3. the player-reported duration of the loaded item.
    ///
    /// The summed-tracks step is skipped when it totals zero, so talks whose
    /// chapter metadata lacks durations still fall back to the real player
    /// duration instead of reporting 0.
    static func totalDurationSeconds(talkDurationSeconds: Int, tracks: [Track], playerDurationMs: Int64) -> Int {
        if talkDurationSeconds > 0 { return talkDurationSeconds }
        let summed = tracks.reduce(0) { $0 + $1.durationSeconds }
        if summed > 0 { return summed }
        return max(0, Int(playerDurationMs / 1000))
    }
}
