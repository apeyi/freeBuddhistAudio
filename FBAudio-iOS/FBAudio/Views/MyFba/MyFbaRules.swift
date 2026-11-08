import Foundation

/// My FBA rules and copy (SPEC "My FBA", "Continue listening (See all)"), pure so they're
/// unit-tested. Mirrors Android `MyFbaRules`. Positions in `RecentlyListened` are cumulative
/// across chapters, so they compare directly with the talk's total length.
enum MyFbaRules {
    typealias Entry = PersistenceManager.RecentlyListened

    /// A talk leaves Continue listening after this long without a play.
    static let maxAgeDays = 90

    static func remainingSeconds(_ r: Entry) -> Int {
        max(r.totalDurationSeconds - Int(r.positionMs / 1000), 0)
    }

    /// Started, not finished, and played within the last 90 days.
    static func isInProgress(_ r: Entry, now: Date) -> Bool {
        if r.positionMs < TalkProgress.startedAfterMs { return false }
        if now.timeIntervalSince(r.timestamp) > Double(maxAgeDays) * 86_400 { return false }
        return r.totalDurationSeconds <= 0 || remainingSeconds(r) > TalkProgress.finishedWithinSeconds
    }

    static func isFinished(_ r: Entry) -> Bool {
        r.totalDurationSeconds > 0 && remainingSeconds(r) <= TalkProgress.finishedWithinSeconds
    }

    /// Continue listening: talks in progress, most recent first.
    static func continueListening(_ all: [Entry], now: Date) -> [Entry] {
        all.filter { isInProgress($0, now: now) }.sorted { $0.timestamp > $1.timestamp }
    }

    static func progress(_ r: Entry) -> Double {
        guard r.totalDurationSeconds > 0 else { return 0 }
        return min(max(Double(r.positionMs) / 1000 / Double(r.totalDurationSeconds), 0), 1)
    }

    /// "7 talks in progress, most recent first".
    static func inProgressLabel(_ count: Int) -> String {
        count == 1 ? "1 talk in progress" : "\(count) talks in progress, most recent first"
    }

    /// "Speaker · Track 2 · 18 min left"; the track only for chapters after the first.
    static func continueMeta(_ r: Entry) -> String {
        var parts: [String] = []
        if !r.speaker.isEmpty { parts.append(r.speaker) }
        if r.trackIndex > 0 { parts.append("Track \(r.trackIndex + 1)") }
        if r.totalDurationSeconds > 0, let left = PlaybackMath.timeLeftLabel(remainingSeconds(r)) { parts.append(left) }
        return parts.joined(separator: " · ")
    }

    /// Library row value: "None yet", "1 talk · 64 MB", "12 talks · 1.2 GB".
    static func downloadsSummary(count: Int, bytes: Int64) -> String {
        count == 0 ? "None yet" : "\(count) \(count == 1 ? "talk" : "talks") · \(formatBytes(bytes))"
    }

    /// "412 MB", "1.2 GB", "80 KB" (decimal units, as the system storage screens use).
    static func formatBytes(_ bytes: Int64) -> String {
        if bytes >= 1_000_000_000 { return String(format: "%.1f GB", Double(bytes) / 1e9) }
        if bytes >= 1_000_000 { return "\(bytes / 1_000_000) MB" }
        if bytes > 0 { return "\(max(bytes / 1000, 1)) KB" }
        return "0 MB"
    }

    /// Listening history headings: "Today", "Yesterday", "Monday 3 November", "3 November 2025".
    static func historyDayLabel(_ date: Date, now: Date, calendar: Calendar = .current) -> String {
        if calendar.isDate(date, inSameDayAs: now) { return "Today" }
        if let y = calendar.date(byAdding: .day, value: -1, to: now), calendar.isDate(date, inSameDayAs: y) { return "Yesterday" }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.timeZone = calendar.timeZone
        f.dateFormat = calendar.component(.year, from: date) == calendar.component(.year, from: now) ? "EEEE d MMMM" : "d MMMM yyyy"
        return f.string(from: date)
    }
}
