import Foundation
import Combine

/// User settings shown on My FBA. Backed by UserDefaults.
@MainActor
final class AppSettings: ObservableObject {
    static let shared = AppSettings()

    private let defaults = UserDefaults.standard
    private static let englishOnlyKey = "english_only"
    private static let preferRemasteredKey = "prefer_remastered"

    /// Hide talks and lists in languages other than English (default on).
    @Published var englishOnly: Bool {
        didSet { defaults.set(englishOnly, forKey: Self.englishOnlyKey) }
    }

    /// Play the remastered version when a talk has one (default on).
    @Published var preferRemastered: Bool {
        didSet { defaults.set(preferRemastered, forKey: Self.preferRemasteredKey) }
    }

    init() {
        englishOnly = defaults.object(forKey: Self.englishOnlyKey) as? Bool ?? true
        preferRemastered = defaults.object(forKey: Self.preferRemasteredKey) as? Bool ?? true
    }

    /// Per-talk override of the remastered/original choice; nil = follow the setting.
    func remasterChoice(_ catNum: String) -> Bool? {
        defaults.object(forKey: "remaster_\(catNum)") as? Bool
    }

    func setRemasterChoice(_ catNum: String, useRemaster: Bool) {
        defaults.set(useRemaster, forKey: "remaster_\(catNum)")
    }

    /// Resolved choice for a talk: explicit per-talk choice, else the global default.
    func useRemaster(_ catNum: String) -> Bool {
        remasterChoice(catNum) ?? preferRemastered
    }
}
