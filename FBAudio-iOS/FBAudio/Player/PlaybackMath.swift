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
        if isPlausibleDuration(talkDurationSeconds) { return talkDurationSeconds }
        let summed = tracks.reduce(0) { $0 + max($1.durationSeconds, 0) }
        if isPlausibleDuration(summed) { return summed }
        return max(Int(playerDurationMs / 1000), 0)
    }

    /// The website's duration field is sometimes garbage (e.g. 717,860,544 s ≈ 22
    /// years for LOC3883). Anything longer than the longest audiobook on the site
    /// is treated as missing and derived from the tracks / player instead.
    static let maxPlausibleSeconds = 100 * 3600

    static func isPlausibleDuration(_ seconds: Int) -> Bool { seconds >= 1 && seconds <= maxPlausibleSeconds }

    /// Position to resume at after switching between the remastered and original
    /// recording: the same absolute time, clamped to the new track's duration
    /// (the two versions differ by a few seconds). Unknown duration → unchanged.
    static func clampPosition(positionMs: Int64, newDurationMs: Int64?) -> Int64 {
        let pos = max(positionMs, 0)
        guard let d = newDurationMs, d > 0 else { return pos }
        return min(pos, max(d - 1000, 0))
    }
}
