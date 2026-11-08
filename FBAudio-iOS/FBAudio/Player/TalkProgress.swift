import Foundation

/// Resume and listening-progress rules for talk pages (SPEC "Talk page, Resume (partly
/// listened)"), pure so they're unit-tested. Mirrors Android `TalkProgress`.
enum TalkProgress {
    /// A talk counts as listened once played to within about the last minute.
    static let finishedWithinSeconds = 60
    /// Below this, a first-track position is a stray tap, not a talk in progress.
    static let startedAfterMs: Int64 = 5_000

    struct Resume: Equatable {
        let trackIndex: Int
        let positionMs: Int64
        let remainingSeconds: Int
    }

    enum TrackState { case notStarted, listened, inProgress }

    /// Where to resume, or nil to show plain "Play" (never started, or finished).
    /// `savedTrack`/`savedPositionMs` is the saved point (account checkpoint already applied
    /// by the caller); `totalSeconds` is the talk's best-known length.
    static func resume(tracks: [Track], totalSeconds: Int, savedTrack: Int, savedPositionMs: Int64) -> Resume? {
        if savedTrack <= 0 && savedPositionMs < startedAfterMs { return nil }
        let index = min(max(savedTrack, 0), max(tracks.count - 1, 0))
        let position = max(savedPositionMs, 0)
        let remaining = PlaybackMath.remainingSeconds(totalSeconds: totalSeconds, tracks: tracks,
                                                      trackIndex: index, positionInTrackMs: position)
        if totalSeconds > 0 && remaining <= finishedWithinSeconds { return nil }
        return Resume(trackIndex: index, positionMs: position, remainingSeconds: remaining)
    }

    static func trackState(_ index: Int, _ resume: Resume?) -> TrackState {
        guard let resume else { return .notStarted }
        if index < resume.trackIndex { return .listened }
        if index == resume.trackIndex { return .inProgress }
        return .notStarted
    }

    /// How far through its own track the in-progress track is, 0...1 (0 when unknown).
    static func trackFraction(_ track: Track, positionMs: Int64) -> Double {
        guard track.durationSeconds > 0 else { return 0 }
        return min(max(Double(positionMs) / 1000 / Double(track.durationSeconds), 0), 1)
    }

    /// Seconds left in one track.
    static func trackRemainingSeconds(_ track: Track, positionMs: Int64) -> Int {
        max(track.durationSeconds - Int(positionMs / 1000), 0)
    }

    /// Line under the Resume button: "Track 2 · 18 min left", or "18 min left" for one track.
    static func resumeLabel(_ resume: Resume, trackCount: Int) -> String {
        [trackCount > 1 ? "Track \(resume.trackIndex + 1)" : nil,
         PlaybackMath.timeLeftLabel(resume.remainingSeconds)]
            .compactMap { $0 }.joined(separator: " · ")
    }

    /// Talk length as the design writes it: "59 min", "90 min", then "2 hr 5 min", "8 hr 14 min".
    static func lengthLabel(_ seconds: Int) -> String {
        let minutes = (seconds + 30) / 60
        if minutes < 120 { return "\(minutes) min" }
        let h = minutes / 60, m = minutes % 60
        return m == 0 ? "\(h) hr" : "\(h) hr \(m) min"
    }
}
