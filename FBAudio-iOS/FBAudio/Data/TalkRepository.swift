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
            // Fetch from web, fill chapter lengths the site lacks, add the v2 artwork/place
            // fields the talk page needs, and cache. The v2 call runs alongside the scrape
            // and is best-effort: Order-only (403) or a network blip leaves the scrape as is.
            async let extras = try? V2Catalogue.getTalkExtras(catNum)
            let scraped = await scraper.fillMissingTrackDurations(try await scraper.fetchTalkDetail(catNum))
            let talk = Self.withExtras(scraped, await extras)
            persistence.cacheTalk(talk, key: catNum)
            return talk
        } catch {
            return nil
        }
    }

    /// Talks related to `catNum` (v2), for the talk page's Related talks tab and Up next.
    func getRelatedTalks(_ catNum: String) async -> [SearchResult] {
        ((try? await V2Catalogue.getRelatedTalks(catNum)) ?? []).filter { $0.catNum != catNum }
    }

    /// Merge v2 extras into a scraped talk; scraped series info wins when present.
    static func withExtras(_ talk: Talk, _ e: V2Catalogue.TalkExtras?) -> Talk {
        guard let e else { return talk }
        let seriesHref = !talk.seriesHref.isEmpty ? talk.seriesHref
            : (e.seriesCatNum.isEmpty ? "" : "/series/details?num=\(e.seriesCatNum)")
        return Talk(catNum: talk.catNum, title: talk.title, speaker: talk.speaker, year: talk.year,
                    genre: talk.genre, durationSeconds: talk.durationSeconds, imageUrl: talk.imageUrl,
                    audioUrl: talk.audioUrl, description: talk.description, tracks: talk.tracks,
                    transcriptUrl: talk.transcriptUrl,
                    series: talk.series.isEmpty ? e.seriesTitle : talk.series, seriesHref: seriesHref,
                    omOnly: talk.omOnly, checkpoint: talk.checkpoint,
                    bannerUrl: e.bannerUrl, speakerImageUrl: e.speakerImageUrl,
                    placeName: e.placeName, placeSlug: e.placeSlug, extrasVersion: 1)
    }

    /// Talks cached before remastered audio / track ids were parsed have tracks
    /// without ids — those are refetched once.
    private static func isCurrentSchema(_ talk: Talk) -> Bool {
        // Cached before the v1.0 talk page fields (banner, place…) were merged in.
        if talk.extrasVersion < 1 { return false }
        if talk.tracks.isEmpty { return true }
        guard talk.tracks.contains(where: { !$0.trackId.isEmpty }) else { return false }
        // Chapters without a length are now estimated from file sizes — refetch once.
        let known = talk.tracks.contains { $0.durationSeconds > 0 }
        let missing = talk.tracks.contains { $0.durationSeconds <= 0 }
        return !(known && missing)
    }

    func searchAudio(_ query: String, language: String? = nil) async throws -> [SearchResult] {
        try await V2Catalogue.searchTalks(query, language: language)
    }

    func searchSeries(_ query: String) async throws -> [SearchResult] {
        try await V2Catalogue.searchSeries(query)
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
