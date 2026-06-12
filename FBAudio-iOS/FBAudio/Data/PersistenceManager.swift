import Foundation

/// Simple file-based persistence for talk cache and recently listened.
class PersistenceManager {
    static let shared = PersistenceManager()

    private let defaults = UserDefaults.standard
    private let fileManager = FileManager.default

    private var cacheDir: URL {
        fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("talks")
    }

    init() {
        try? fileManager.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        pruneTalkCache()
        pruneStalePlaybackKeys()
    }

    // MARK: - Talk Cache

    private func cacheFileName(_ catNum: String) -> String {
        // Sanitized: catNums can arrive from deep links, and raw values would be
        // interpreted as path components.
        let safe = catNum.replacing(/[^a-zA-Z0-9_-]/, with: "")
        return "\(safe).json"
    }

    /// Cache under the REQUESTED catNum, not the one in the response — any
    /// casing/format difference between them made every lookup a cache miss.
    func cacheTalk(_ talk: Talk, key: String) {
        let url = cacheDir.appendingPathComponent(cacheFileName(key))
        if let data = try? JSONEncoder().encode(talk) {
            try? data.write(to: url)
        }
    }

    func getCachedTalk(_ catNum: String) -> Talk? {
        let url = cacheDir.appendingPathComponent(cacheFileName(catNum))
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(Talk.self, from: data)
    }

    /// Drop cached talk files older than 30 days (mirrors Android's prune).
    private func pruneTalkCache() {
        let cutoff = Date().addingTimeInterval(-30 * 24 * 3600)
        guard let files = try? fileManager.contentsOfDirectory(
            at: cacheDir, includingPropertiesForKeys: [.contentModificationDateKey]) else { return }
        for file in files {
            let modified = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
            if let modified, modified < cutoff {
                try? fileManager.removeItem(at: file)
            }
        }
    }

    // MARK: - Recently Listened

    struct RecentlyListened: Codable, Identifiable {
        let catNum: String
        let title: String
        let speaker: String
        let imageUrl: String
        var positionMs: Int64
        var trackIndex: Int
        var totalDurationSeconds: Int
        var timestamp: Date

        var id: String { catNum }
    }

    private let recentlyListenedKey = "recently_listened"
    private let maxRecentlyListened = 20

    func getRecentlyListened() -> [RecentlyListened] {
        guard let data = defaults.data(forKey: recentlyListenedKey) else { return [] }
        return (try? JSONDecoder().decode([RecentlyListened].self, from: data)) ?? []
    }

    func updateRecentlyListened(_ entry: RecentlyListened) {
        var list = getRecentlyListened()
        list.removeAll { $0.catNum == entry.catNum }
        list.insert(entry, at: 0)
        if list.count > maxRecentlyListened {
            list = Array(list.prefix(maxRecentlyListened))
        }
        if let data = try? JSONEncoder().encode(list) {
            defaults.set(data, forKey: recentlyListenedKey)
        }
    }

    // MARK: - Playback State

    func savePlaybackState(catNum: String, position: Int64, trackIndex: Int, duration: Int64) {
        defaults.set(catNum, forKey: "last_cat_num")
        defaults.set(position, forKey: "last_position_\(catNum)")
        defaults.set(trackIndex, forKey: "last_track_index_\(catNum)")
        defaults.set(duration, forKey: "last_duration_\(catNum)")
    }

    func getLastCatNum() -> String? {
        defaults.string(forKey: "last_cat_num")
    }

    func getLastPosition(_ catNum: String) -> Int64 {
        Int64(defaults.integer(forKey: "last_position_\(catNum)"))
    }

    func getLastTrackIndex(_ catNum: String) -> Int {
        defaults.integer(forKey: "last_track_index_\(catNum)")
    }

    func getLastDuration(_ catNum: String) -> Int64 {
        Int64(defaults.integer(forKey: "last_duration_\(catNum)"))
    }

    /// Per-talk playback keys (last_position_X etc.) otherwise accumulate in
    /// UserDefaults forever. Resume positions only matter for recently played
    /// talks, so drop keys for anything that has fallen out of the recent list.
    private func pruneStalePlaybackKeys() {
        let keep = Set(getRecentlyListened().map(\.catNum) + [getLastCatNum()].compactMap { $0 })
        let prefixes = ["last_position_", "last_track_index_", "last_duration_"]
        for key in defaults.dictionaryRepresentation().keys {
            for prefix in prefixes where key.hasPrefix(prefix) {
                let catNum = String(key.dropFirst(prefix.count))
                if !keep.contains(catNum) {
                    defaults.removeObject(forKey: key)
                }
            }
        }
    }

    // MARK: - Playback Speed

    var playbackSpeed: Float {
        get { defaults.float(forKey: "playback_speed").nonZero ?? 1.0 }
        set { defaults.set(newValue, forKey: "playback_speed") }
    }
}

private extension Float {
    var nonZero: Float? { self == 0 ? nil : self }
}
