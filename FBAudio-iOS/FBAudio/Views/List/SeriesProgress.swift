import Foundation

/// Series page progress (SPEC "Series page, Resume with listened and in progress rows"), pure so
/// it's unit-tested; mirrors Android `SeriesProgress`.
enum SeriesProgress {
    struct Saved: Equatable { let positionMs: Int64; let totalSeconds: Int; let listenedAt: Date }
    enum State { case notStarted, inProgress, listened }
    struct Resume: Equatable { let index: Int; let catNum: String; let remainingSeconds: Int? }

    static func state(_ saved: Saved?) -> State {
        guard let saved else { return .notStarted }
        if saved.totalSeconds > 0 && Int(saved.positionMs / 1000) >= saved.totalSeconds - TalkProgress.finishedWithinSeconds {
            return .listened
        }
        return saved.positionMs >= TalkProgress.startedAfterMs ? .inProgress : .notStarted
    }

    /// "Resume Talk n": the talk played most recently if it's unfinished, else the next talk after
    /// the last one finished. nil (show "Play all") when nothing has been heard or all is finished.
    static func resume(members: [String], saved: [String: Saved]) -> Resume? {
        let states = members.map { state(saved[$0]) }
        guard states.contains(where: { $0 != .notStarted }) else { return nil }
        guard let latest = members.indices.filter({ states[$0] != .notStarted })
            .max(by: { saved[members[$0]]!.listenedAt < saved[members[$1]]!.listenedAt }) else { return nil }
        let index: Int
        if states[latest] == .inProgress {
            index = latest
        } else if let next = (latest + 1..<members.count).first(where: { states[$0] != .listened })
                    ?? members.indices.first(where: { states[$0] != .listened }) {
            index = next
        } else {
            return nil
        }
        var remaining: Int? = nil
        if states[index] == .inProgress, let s = saved[members[index]], s.totalSeconds > 0 {
            remaining = max(s.totalSeconds - Int(s.positionMs / 1000), 0)
        }
        return Resume(index: index, catNum: members[index], remainingSeconds: remaining)
    }
}
