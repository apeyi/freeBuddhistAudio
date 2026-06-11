import Foundation

@MainActor
class TalkRepository: ObservableObject {
    static let shared = TalkRepository()

    private let scraper = FBAScraper()
    private let persistence = PersistenceManager.shared

    func getTalkDetail(_ catNum: String) async -> Talk? {
        if let cached = persistence.getCachedTalk(catNum) {
            return cached
        }
        do {
            let talk = try await scraper.fetchTalkDetail(catNum)
            persistence.cacheTalk(talk)
            return talk
        } catch {
            return nil
        }
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
}
