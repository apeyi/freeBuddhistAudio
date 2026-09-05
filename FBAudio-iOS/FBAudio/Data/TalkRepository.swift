import Foundation

@MainActor
class TalkRepository: ObservableObject {
    static let shared = TalkRepository()

    private let scraper = FBAScraper()
    private let persistence = PersistenceManager.shared

    /// `forceRefresh` bypasses the cache — used when logged in, so the page carries
    /// the account's saved position and Order-only visibility.
    func getTalkDetail(_ catNum: String, forceRefresh: Bool = false) async -> Talk? {
        if !forceRefresh, let cached = persistence.getCachedTalk(catNum), Self.isCurrentSchema(cached) {
            return cached
        }
        do {
            // Fetch from web, fill chapter lengths the site lacks, and cache
            let talk = await scraper.fillMissingTrackDurations(try await scraper.fetchTalkDetail(catNum))
            persistence.cacheTalk(talk, key: catNum)
            return talk
        } catch {
            return nil
        }
    }

    /// Talks cached before remastered audio / track ids were parsed have tracks
    /// without ids — those are refetched once.
    private static func isCurrentSchema(_ talk: Talk) -> Bool {
        if talk.tracks.isEmpty { return true }
        guard talk.tracks.contains(where: { !$0.trackId.isEmpty }) else { return false }
        // Chapters without a length are now estimated from file sizes — refetch once.
        let known = talk.tracks.contains { $0.durationSeconds > 0 }
        let missing = talk.tracks.contains { $0.durationSeconds <= 0 }
        return !(known && missing)
    }

    func searchAudio(_ query: String) async throws -> [SearchResult] {
        try await scraper.searchAudio(query)
    }

    func searchSeries(_ query: String) async throws -> [SearchResult] {
        try await scraper.searchSeries(query)
    }

    func browseBySpeaker(_ name: String) async throws -> BrowsePage {
        try await scraper.browseBySpeaker(name)
    }

    func getBrowseCategories() -> [BrowseCategory] {
        scraper.fetchBrowseCategories()
    }

    func getTalksByBrowseUrl(_ browseUrl: String) async throws -> BrowsePage {
        try await scraper.fetchFromBrowseUrl(browseUrl)
    }

    func fetchMoreItems(apiBaseUrl: String, browseQueryString: String, startIndex: Int, count: Int) async -> [SearchResult] {
        await scraper.fetchMoreItems(apiBaseUrl: apiBaseUrl, browseQueryString: browseQueryString,
                                      startIndex: startIndex, count: count)
    }

    func fetchTranscript(_ transcriptUrl: String) async throws -> String {
        try await scraper.fetchTranscript(transcriptUrl)
    }

    /// Fresh fetch when logged in (saved position, Order-only visibility), cached otherwise.
    func getTalkDetailForPlayback(_ catNum: String) async -> Talk? {
        if AuthRepository.shared.isLoggedIn, let fresh = await getTalkDetail(catNum, forceRefresh: true) {
            return fresh
        }
        return await getTalkDetail(catNum)
    }
}
