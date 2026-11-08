import Foundation

/// Sleep timer choices (SPEC "Sleep timer sheet"). Mirrors Android `SleepOption`.
enum SleepOption: CaseIterable, Equatable {
    case off, min5, min15, min30, min45, hour1, endOfTrack, endOfTalk

    var label: String {
        switch self {
        case .off: return "Off"
        case .min5: return "5 minutes"
        case .min15: return "15 minutes"
        case .min30: return "30 minutes"
        case .min45: return "45 minutes"
        case .hour1: return "1 hour"
        case .endOfTrack: return "End of this track"
        case .endOfTalk: return "End of this talk"
        }
    }

    /// Short form for the More sheet row.
    var shortLabel: String {
        switch self {
        case .off: return "Off"
        case .hour1: return "1 hr"
        case .endOfTrack: return "End of track"
        case .endOfTalk: return "End of talk"
        default: return "\(minutes ?? 0) min"
        }
    }

    var minutes: Int? {
        switch self {
        case .min5: return 5
        case .min15: return 15
        case .min30: return 30
        case .min45: return 45
        case .hour1: return 60
        default: return nil
        }
    }
}

/// Pure fade maths, unit-tested.
enum SleepFade {
    static let fadeSeconds: TimeInterval = 10

    /// Volume (0...1) with `remaining` seconds left: full until the last 10 s, then linear.
    static func volume(remaining: TimeInterval) -> Float {
        if remaining >= fadeSeconds { return 1 }
        if remaining <= 0 { return 0 }
        return Float(remaining / fadeSeconds)
    }
}
