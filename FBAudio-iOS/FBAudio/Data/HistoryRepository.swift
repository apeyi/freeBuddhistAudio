import Foundation

/// Two-way sync of listening history with the FBA account, using what the
/// website already has: a per-user history and a per-talk resume position
/// ("checkpoint"). Best-effort and silent — the local store stays the source of
/// truth offline. Mirrors Android's HistoryRepository.
@MainActor
final class HistoryRepository {
    static let shared = HistoryRepository()

    private static let base = "https://www.freebuddhistaudio.com"
    private let auth = AuthRepository.shared
    private let persistence = PersistenceManager.shared

    /// "2026-09-05T17:43:20.223708+0000" → Date (nil if unparseable).
    static func parseListenTime(_ value: String) -> Date? {
        let trimmed = value.replacing(/\.\d+/, with: "")
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ"
        if let d = f.date(from: trimmed) { return d }
        return ISO8601DateFormatter().date(from: trimmed)
    }

    /// Merge the account's history into Recently listened. Talks not yet known
    /// locally are added; known talks keep their local position and take the
    /// newer of the two timestamps.
    func syncFromServer(maxItems: Int = 30) async {
        guard auth.isLoggedIn else { return }
        guard let json = await getJson("\(Self.base)/api/v1/history?maxItems=\(maxItems)"),
              let items = json["historyItems"] as? [[String: Any]] else { return }
        var local = persistence.getRecentlyListened()
        var changed = false
        for item in items {
            guard let talk = item["talk"] as? [String: Any],
                  let catNum = talk["cat_num"] as? String,
                  let time = (item["listenTime"] as? String).flatMap(Self.parseListenTime) else { continue }
            if let idx = local.firstIndex(where: { $0.catNum == catNum }) {
                if time > local[idx].timestamp {
                    local[idx].timestamp = time
                    changed = true
                }
            } else {
                let image = (talk["image"] as? String) ?? ""
                local.append(PersistenceManager.RecentlyListened(
                    catNum: catNum,
                    title: HTMLEntities.unescape((talk["title"] as? String) ?? ""),
                    speaker: HTMLEntities.unescape((talk["speaker"] as? String) ?? ""),
                    imageUrl: image.hasPrefix("http") || image.isEmpty ? image : Self.base + image,
                    positionMs: 0, trackIndex: 0, totalDurationSeconds: 0, timestamp: time
                ))
                changed = true
            }
        }
        if changed {
            persistence.replaceRecentlyListened(local.sorted { $0.timestamp > $1.timestamp })
        }
    }

    /// The website records a "stream start" per play; mirror it so web history shows app listens.
    func recordStreamStart(_ catNum: String) async {
        guard auth.isLoggedIn else { return }
        await postJson("\(Self.base)/api/v1/history",
                       ["cat_num": catNum, "item_type": "talk", "action_type": "stream_start"])
    }

    /// Save the resume position on the account (the website does this every few seconds while playing).
    func postCheckpoint(catNum: String, trackId: String, positionSeconds: Int) async {
        guard auth.isLoggedIn, !trackId.isEmpty else { return }
        await postJson("\(Self.base)/api/v1/checkpoints/",
                       ["catNum": catNum, "trackId": trackId, "timeSeconds": positionSeconds])
    }

    private func getJson(_ url: String) async -> [String: Any]? {
        guard let u = URL(string: url) else { return nil }
        var request = URLRequest(url: u)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    private func postJson(_ url: String, _ body: [String: Any]) async {
        guard let u = URL(string: url), let data = try? JSONSerialization.data(withJSONObject: body) else { return }
        var request = URLRequest(url: u)
        request.httpMethod = "POST"
        request.httpBody = data
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        _ = try? await URLSession.shared.data(for: request) // offline or session gone — silent
    }
}
