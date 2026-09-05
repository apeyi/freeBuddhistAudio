import Foundation

/// Website content beyond single talks: the curated menu, collections, series,
/// Digital Legacy. Cached on disk (24 h) so Home/Collections open offline; when
/// the network fails a stale cache entry is still returned.
@MainActor
final class ContentRepository {
    static let shared = ContentRepository()

    private let scraper = FBAScraper()
    private let cache = ContentCache()
    private var menuMemo: [MenuNode]?
    private var pageMemo: [String: ListPage] = [:]

    private struct MenuEnvelope: Codable { let nodes: [MenuNode] }

    /// The full curated menu (not language-filtered).
    func getMenu(forceRefresh: Bool = false) async throws -> [MenuNode] {
        if !forceRefresh, let memo = menuMemo { return memo }
        let cached: (MenuEnvelope, Bool)? = await cache.get("menu", MenuEnvelope.self)
        if !forceRefresh, let cached, cached.1, !cached.0.nodes.isEmpty {
            menuMemo = cached.0.nodes
            return cached.0.nodes
        }
        do {
            let fresh = try await scraper.fetchSiteMenu()
            if !fresh.isEmpty {
                menuMemo = fresh
                await cache.put("menu", MenuEnvelope(nodes: fresh))
                return fresh
            }
        } catch {
            if cached == nil || cached!.0.nodes.isEmpty { throw error }
        }
        let stale = cached?.0.nodes ?? []
        if !stale.isEmpty { menuMemo = stale }
        return stale
    }

    private var englishOnly: Bool { AppSettings.shared.englishOnly }

    /// Children of a nested menu path, e.g. ["collections", "meditation & mindfulness"].
    func getNodeChildren(_ path: [String]) async throws -> [MenuNode] {
        var nodes = try await getMenu()
        for label in path {
            guard let next = nodes.first(where: { $0.label.caseInsensitiveCompare(label) == .orderedSame }) else { return [] }
            nodes = next.children
        }
        return LanguageFilter.filterMenu(nodes, englishOnly: englishOnly)
    }

    /// Curated collections for the Collections grid.
    func getCollectionTiles() async throws -> [MenuNode] {
        LanguageFilter.filterMenu(SiteMenuParser.collectionTiles(try await getMenu()), englishOnly: englishOnly)
    }

    /// Load one page of a source. Page 1 of each source is cached for offline use.
    func getPage(_ source: ContentSource, page: Int, previous: ListPage? = nil) async throws -> ListPage {
        let key = "page:\(source.encode()):\(page)"
        if page == 1, let memo = pageMemo[key] { return await applyLanguage(memo) }

        func fetch() async throws -> ListPage {
            switch source {
            case .apiCollection(let type, let title):
                return try await scraper.fetchApiCollectionPage(type: type, page: page, title: title)
            case .namedCollection(let slug):
                return try await scraper.fetchNamedCollectionPage(slug: slug, page: page)
            case .browse(let path):
                return try await scraper.fetchBrowsePage(path: path, page: page,
                                                         apiUrl: previous?.apiUrl ?? "", apiQuery: previous?.apiQuery ?? "")
            case .series(let path):
                return try await scraper.fetchSeriesPage(path: path)
            }
        }

        let result: ListPage
        if page == 1 {
            let cached: (ListPage, Bool)? = await cache.get(key, ListPage.self)
            if let cached, cached.1 {
                result = cached.0
            } else {
                do {
                    result = try await fetch()
                    await cache.put(key, result)
                } catch {
                    guard let cached else { throw error }
                    result = cached.0
                }
            }
            if pageMemo.count >= 40 { pageMemo.removeAll() }
            pageMemo[key] = result
        } else {
            result = try await fetch()
        }
        return await applyLanguage(result)
    }

    private func applyLanguage(_ page: ListPage) async -> ListPage {
        guard englishOnly else { return page }
        let menu = (try? await getMenu()) ?? []
        var copy = page
        copy.items = LanguageFilter.filterItems(page.items, englishOnly: true,
                                                nonEnglishSpeakers: LanguageFilter.nonEnglishSpeakers(menu),
                                                nonEnglishCentres: LanguageFilter.nonEnglishCentres(menu))
        return copy
    }

    /// Language-filter any list (e.g. search results).
    func filterForLanguage(_ items: [SearchResult]) async -> [SearchResult] {
        guard englishOnly else { return items }
        let menu = (try? await getMenu()) ?? []
        return LanguageFilter.filterItems(items, englishOnly: true,
                                          nonEnglishSpeakers: LanguageFilter.nonEnglishSpeakers(menu),
                                          nonEnglishCentres: LanguageFilter.nonEnglishCentres(menu))
    }

    private struct ImageMap: Codable { let images: [String: String] }

    /// Images for the curated People / Places lists, from FBA's speaker and place
    /// indexes (keyed by normalized browse path). Cached; empty on failure.
    func getIndexImages(_ type: String) async -> [String: String] {
        let key = "images:\(type)"
        let cached: (ImageMap, Bool)? = await cache.get(key, ImageMap.self)
        if let cached, cached.1 { return cached.0.images }
        if let fresh = try? await scraper.fetchIndexImages(type: type) {
            await cache.put(key, ImageMap(images: fresh))
            return fresh
        }
        return cached?.0.images ?? [:]
    }

    private struct EntryList: Codable { let items: [SearchResult] }

    /// All speakers or places as browse links (cached daily), language-filtered.
    func getIndexEntries(_ type: String) async -> [SearchResult] {
        let key = "index:\(type)"
        let cached: (EntryList, Bool)? = await cache.get(key, EntryList.self)
        var items: [SearchResult]
        if let cached, cached.1 {
            items = cached.0.items
        } else if let fresh = try? await scraper.fetchIndexEntries(type: type) {
            await cache.put(key, EntryList(items: fresh))
            items = fresh
        } else {
            items = cached?.0.items ?? []
        }
        // Index tiles carry the name in `title`; expose it as speaker/centre so the
        // language filter can apply its lists, then strip it again for display.
        let tagged = items.map { i in
            SearchResult(catNum: i.catNum, title: i.title, speaker: type == "speakers" ? i.title : "", imageUrl: i.imageUrl,
                         path: i.path, year: i.year, centre: type == "places" ? i.title : "", omOnly: i.omOnly)
        }
        return await filterForLanguage(tagged).map { i in
            SearchResult(catNum: i.catNum, title: i.title, speaker: "", imageUrl: i.imageUrl, path: i.path, year: i.year, centre: "", omOnly: i.omOnly)
        }
    }

    /// Curated collections + themes as `/collection/` links (for search).
    func getCollectionEntries() async -> [SearchResult] {
        guard let menu = try? await getMenu() else { return [] }
        let nodes = SiteMenuParser.collectionTiles(menu)
            + (SiteMenuParser.section(menu, "themes")?.children ?? []).filter { $0.collectionSlug != nil }
        var seen = Set<String>()
        return LanguageFilter.filterMenu(nodes, englishOnly: englishOnly).compactMap { node in
            guard let slug = node.collectionSlug, seen.insert(slug).inserted else { return nil }
            return SearchResult(catNum: slug, title: node.label, path: "/collection/\(slug)")
        }
    }

    struct NameMatches {
        var speakers: [SearchResult] = []
        var places: [SearchResult] = []
        var collections: [SearchResult] = []
    }

    /// Speakers, places and collections whose name contains the query (for the search screen).
    func matchNames(_ query: String) async -> NameMatches {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard q.count >= 2 else { return NameMatches() }
        func matching(_ items: [SearchResult]) -> [SearchResult] { Array(items.filter { $0.title.lowercased().contains(q) }.prefix(20)) }
        return NameMatches(
            speakers: matching(await getIndexEntries("speakers")),
            places: matching(await getIndexEntries("places")),
            collections: matching(await getCollectionEntries())
        )
    }

    func getDigitalLegacy() async -> DigitalLegacy? {
        let cached: (DigitalLegacy, Bool)? = await cache.get("digital_legacy", DigitalLegacy.self)
        if let cached, cached.1 { return cached.0 }
        if let fresh = try? await scraper.fetchDigitalLegacy() {
            await cache.put("digital_legacy", fresh)
            return fresh
        }
        return cached?.0
    }

    /// Drop in-memory page copies (e.g. after the language setting changes).
    func invalidateMemo() { pageMemo.removeAll() }
}

/// Small on-disk JSON cache with a TTL; stale entries are still readable.
actor ContentCache {
    private let dir: URL
    private let ttl: TimeInterval

    private struct Envelope<T: Codable>: Codable { let savedAt: Date; let value: T }

    init(ttl: TimeInterval = 24 * 3600) {
        self.ttl = ttl
        dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("content")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    private func file(_ key: String) -> URL {
        let safe = key.replacing(/[^a-zA-Z0-9_.-]/, with: "_")
        return dir.appendingPathComponent(safe + ".json")
    }

    func put<T: Codable>(_ key: String, _ value: T) {
        if let data = try? JSONEncoder().encode(Envelope(savedAt: Date(), value: value)) {
            try? data.write(to: file(key), options: .atomic)
        }
    }

    /// Returns the value and whether it is still fresh; nil when absent or unreadable.
    func get<T: Codable>(_ key: String, _ type: T.Type) -> (T, Bool)? {
        guard let data = try? Data(contentsOf: file(key)),
              let env = try? JSONDecoder().decode(Envelope<T>.self, from: data) else { return nil }
        return (env.value, Date().timeIntervalSince(env.savedAt) < ttl)
    }
}
