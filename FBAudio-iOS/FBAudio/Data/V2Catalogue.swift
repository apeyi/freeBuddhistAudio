import Foundation

/// Reads the website catalogue from `/api/v2` via the generated client and maps
/// the v2 models back onto the app's domain shapes (`ListPage`, `SearchResult`,
/// `MenuNode`, `DigitalLegacy`), so the screens and `ContentSource` routing are
/// unchanged — only the data source moved off the HTML scraper. Anonymous only;
/// talk detail / account data stay on the cookie path until the v2 auth ships.
enum V2Catalogue {
    private static let PAGE = ListPage.pageSize
    private static let INDEX_PAGE = 1000

    // MARK: - lists / collections / browse / series

    static func getPage(_ source: ContentSource, page: Int) async throws -> ListPage {
        switch source {
        case .apiCollection(let type, let title): return try await apiCollection(type, title: title, page: page)
        case .namedCollection(let slug): return toListPage(try await CatalogueAPI.getCollection(slug: slug, page: page, pageSize: PAGE))
        case .browse(let path): return try await browse(path, page: page)
        case .series(let path): return toListPage(try await CatalogueAPI.getSeries(catNum: extractNum(path)))
        }
    }

    private static func apiCollection(_ type: String, title: String, page: Int) async throws -> ListPage {
        if let kind = listKind(type) {
            return toListPage(try await CatalogueAPI.getList(kind: kind, page: page, pageSize: PAGE), titleOverride: title)
        }
        if let index = try await indexPage(type, page: page, pageSize: PAGE) {
            return toListPage(index, titleOverride: title)
        }
        return ListPage(items: [], totalItems: 0, page: page, title: title)
    }

    private static func indexPage(_ type: String, page: Int, pageSize: Int) async throws -> V2ListItemPage? {
        switch type.lowercased().replacingOccurrences(of: "_", with: "") {
        case "speakers": return try await CatalogueAPI.listSpeakers(q: nil, page: page, pageSize: pageSize)
        case "places": return try await CatalogueAPI.listPlaces(q: nil, page: page, pageSize: pageSize)
        case "years": return try await CatalogueAPI.listYears(page: page, pageSize: pageSize)
        case "themes": return try await CatalogueAPI.listThemes(page: page, pageSize: pageSize)
        case "genres": return try await CatalogueAPI.listGenres(q: nil, page: page, pageSize: pageSize)
        default: return nil
        }
    }

    private static func browse(_ path: String, page: Int) async throws -> ListPage {
        func param(_ k: String) -> String? {
            guard let v = ContentSource.queryValue(k, in: path), !v.isEmpty else { return nil }
            return v.removingPercentEncoding ?? v
        }
        if let s = param("s") { return toListPage(try await CatalogueAPI.listSpeakerTalks(slug: s, page: page, pageSize: PAGE)) }
        if let p = param("p") { return toListPage(try await CatalogueAPI.listPlaceTalks(slug: p, page: page, pageSize: PAGE)) }
        if let th = param("th") { return toListPage(try await CatalogueAPI.listThemeTalks(slug: th, page: page, pageSize: PAGE)) }
        if let g = param("genre") { return toListPage(try await CatalogueAPI.listGenreTalks(slug: g, page: page, pageSize: PAGE)) }
        if let l = param("lang") { return toListPage(try await CatalogueAPI.listLanguageTalks(code: l, page: page, pageSize: PAGE)) }
        if let y = param("y"), let yr = Int(y) { return toListPage(try await CatalogueAPI.listYearTalks(year: yr, page: page, pageSize: PAGE)) }
        if let cat = param("cat") { return try await apiCollection(cat, title: "", page: page) }
        return ListPage(items: [], totalItems: 0, page: page)
    }

    // MARK: - menu

    static func getMenu() async throws -> [MenuNode] {
        try await ServiceAPI.getMenu().items.map(toDomainNode)
    }

    /// Synthesise the domain MenuNode's `link` from the v2 kind/target so
    /// MenuNode.toSource()/collectionSlug and the English-only label filter keep working.
    private static func toDomainNode(_ n: V2MenuNode) -> MenuNode {
        MenuNode(label: n.label, link: menuLink(n.kind, target: n.target, webUrl: n.webUrl),
                 om: n.omOnly, children: n.children.map(toDomainNode))
    }

    /// The domain MenuNode `link` for a v2 menu kind/target, in the website's URL shapes so
    /// MenuNode.toSource()/collectionSlug/talkCatNum keep routing it. `index` targets
    /// (speakers/places/years/themes) become `/browse?cat=…`, served from the full index endpoints.
    static func menuLink(_ kind: V2MenuNodeKind, target: String?, webUrl: String?) -> String {
        let t = target ?? ""
        switch kind {
        case .list: return "/browse?cat=\(t)"
        case .collection: return "/collection/\(t)"
        case .talk: return "/audio/details?num=\(t)"
        case .series: return "/series/details?num=\(t)"
        case .speaker: return "/browse?s=\(t)"
        case .place: return "/browse?p=\(t)"
        case .theme: return "/browse?th=\(t)"
        case .year: return "/browse?y=\(t)"
        case .language: return "/browse?lang=\(t)"
        case .index: return "/browse?cat=\(t)"
        case .group: return "#"
        case .page, .external, .unknown: return webUrl ?? "#"
        }
    }

    // MARK: - indexes (People/Places lists + fuzzy search)

    static func getIndexEntries(_ type: String) async throws -> [SearchResult] {
        (try await indexPage(type, page: 1, pageSize: INDEX_PAGE)?.items ?? []).map(toSearchResult)
    }

    static func getIndexImages(_ type: String) async throws -> [String: String] {
        var out: [String: String] = [:]
        for item in (try await indexPage(type, page: 1, pageSize: INDEX_PAGE)?.items ?? []) {
            let r = toSearchResult(item)
            if !r.imageUrl.isEmpty { out[FBAScraper.normalizeBrowsePath(r.path)] = r.imageUrl }
        }
        return out
    }

    // MARK: - search

    static func searchTalks(_ query: String) async throws -> [SearchResult] {
        try await SearchAPI.search(kind: .talk, q: query, pageSize: 50).items.map { toSearchResult($0.item) }
    }

    static func searchSeries(_ query: String) async throws -> [SearchResult] {
        try await SearchAPI.search(kind: .series, q: query, pageSize: 50).items.map { toSearchResult($0.item) }
    }

    // MARK: - talk page extras

    /// Fields the v1.0 talk page needs that the scraped talk lacks.
    struct TalkExtras {
        let bannerUrl: String
        let speakerImageUrl: String
        let placeName: String
        let placeSlug: String
        let seriesTitle: String
        let seriesCatNum: String
    }

    static func getTalkExtras(_ catNum: String) async throws -> TalkExtras {
        let t = try await CatalogueAPI.getTalk(catNum: catNum)
        return TalkExtras(bannerUrl: t.marqueeImage ?? "", speakerImageUrl: t.speaker.image ?? "",
                          placeName: t.place?.name ?? "", placeSlug: t.place?.slug ?? "",
                          seriesTitle: t.series.first?.title ?? "", seriesCatNum: t.series.first?.catNum ?? "")
    }

    static func getRelatedTalks(_ catNum: String) async throws -> [SearchResult] {
        try await CatalogueAPI.listRelatedTalks(catNum: catNum, pageSize: 24).items.map(toSearchResult)
    }

    // MARK: - home

    /// Featured posts for the Home carousel (no description or band colour in v2 yet).
    static func getFeatured() async throws -> [Featured] {
        try await ServiceAPI.getHome().highlightedContent.map { Featured(title: $0.title, imageUrl: $0.image ?? "", webUrl: $0.webUrl) }
    }

    // MARK: - digital legacy

    static func getDigitalLegacy() async throws -> DigitalLegacy {
        let p = try await ServiceAPI.getDigitalLegacyPage()
        return DigitalLegacy(title: p.title, description: p.bodyText, sampleCatNum: p.sample.catNum,
                             seriesPath: "/series/details?num=\(p.seriesCatNum)")
    }

    // MARK: - mappers

    private static func toSearchResult(_ i: V2ListItem) -> SearchResult {
        let path: String
        switch i.kind {
        case .talk: path = "/audio/details?num=\(i.id)"
        case .series: path = "/series/details?num=\(i.id)"
        case .speaker: path = "/browse?s=\(i.id)"
        case .place: path = "/browse?p=\(i.id)"
        case .theme: path = "/browse?th=\(i.id)"
        case .genre: path = "/browse?genre=\(i.id)"
        case .year: path = "/browse?y=\(i.id)"
        case .collection: path = "/collection/\(i.id)"
        case .unknown: path = i.webUrl
        }
        return SearchResult(catNum: i.id, title: i.title, speaker: i.subtitle ?? "", imageUrl: i.image ?? "",
                            path: path, year: i.year ?? 0, centre: i.placeName ?? "", omOnly: i.omOnly)
    }

    private static func toListPage(_ p: V2ListItemPage, titleOverride: String? = nil) -> ListPage {
        let title = (titleOverride?.isEmpty == false) ? titleOverride! : (p.title ?? "")
        return ListPage(items: p.items.map(toSearchResult), totalItems: p.paging.total, page: p.paging.page, title: title)
    }

    private static func toListPage(_ p: V2CollectionPage) -> ListPage {
        ListPage(items: p.items.map(toSearchResult), totalItems: p.paging.total, page: p.paging.page,
                 title: p.title, description: p.descriptionText ?? "", imageUrl: p.image ?? "")
    }

    private static func toListPage(_ s: V2Series) -> ListPage {
        ListPage(items: s.members.map(toSearchResult), totalItems: s.members.count, page: 1,
                 title: s.title, description: s.blurbText, imageUrl: s.image ?? "",
                 hasRemaster: s.hasRemaster, omOnly: s.omOnly,
                 speaker: s.speaker?.name ?? "", year: s.year ?? 0, bannerUrl: s.marqueeImage ?? "", catNum: s.catNum)
    }

    private static func extractNum(_ path: String) -> String {
        (ContentSource.queryValue("num", in: path)?.removingPercentEncoding) ?? (ContentSource.queryValue("num", in: path) ?? path)
    }

    private static func listKind(_ type: String) -> V2ListKind? {
        switch type.lowercased().replacingOccurrences(of: "_", with: "") {
        case "latest": return .latest
        case "popular": return .popular
        case "titles": return .titles
        case "introductions": return .introductions
        case "meditation": return .meditation
        case "serieslatest": return .serieslatest
        case "seriessangharakshita": return .seriessangharakshita
        case "seriesotherspeakers": return .seriesotherspeakers
        case "allseries": return .allseries
        case "ordertalks", "order": return .ordertalks
        default: return nil
        }
    }
}
