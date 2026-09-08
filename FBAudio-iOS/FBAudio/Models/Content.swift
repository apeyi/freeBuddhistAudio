import Foundation

/// One entry of the website's curated side menu (collections, themes, people, places…).
struct MenuNode: Codable, Equatable, Hashable, Identifiable {
    let label: String
    let link: String
    let om: Bool
    let children: [MenuNode]

    var id: String { "\(label)|\(link)" }

    init(label: String, link: String, om: Bool = false, children: [MenuNode] = []) {
        self.label = label
        self.link = link
        self.om = om
        self.children = children
    }

    var hasChildren: Bool { !children.isEmpty }
    var isExternal: Bool { link.hasPrefix("http") && !link.contains("freebuddhistaudio.com") }
    var isPlaceholder: Bool { link.isEmpty || link.hasPrefix("#") }

    /// The /collection/<slug> slug when this entry is a named collection.
    var collectionSlug: String? {
        guard let range = link.range(of: "/collection/") else { return nil }
        let rest = link[range.upperBound...]
        let slug = rest.prefix { $0 != "/" && $0 != "?" && $0 != "#" }
        let trimmed = slug.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty ? nil : trimmed
    }

    /// A ContentSource for this entry, or nil when it is a placeholder/external link.
    func toSource() -> ContentSource? {
        if let slug = collectionSlug { return .namedCollection(slug) }
        var path = link.trimmingCharacters(in: .whitespaces)
        if path.hasPrefix("https://www.freebuddhistaudio.com") {
            path = String(path.dropFirst("https://www.freebuddhistaudio.com".count))
        }
        if path.hasPrefix("/series/details") { return .series(path) }
        if path.hasPrefix("/browse") || path.hasPrefix("browse") {
            if let cat = ContentSource.queryValue("cat", in: path) {
                return .apiCollection(cat, title: label)
            }
            return .browse(path.hasPrefix("/") ? path : "/" + path)
        }
        return nil
    }
}

/// Where a list of talks/series comes from. String-encodable for navigation.
enum ContentSource: Hashable, Codable {
    /// `/api/v1/collections/{type}` — latest, introductions, speakers, all_series…
    case apiCollection(String, title: String = "")
    /// A curated `/collection/{slug}` page (themes, collections, some people/places).
    case namedCollection(String)
    /// A `/browse?…` listing: one speaker, place, year or genre.
    case browse(String)
    /// A `/series/details?num=…` page.
    case series(String)

    var isSeries: Bool { if case .series = self { return true } else { return false } }

    func encode() -> String {
        switch self {
        case .apiCollection(let type, let title): return "api|\(type)|\(title)"
        case .namedCollection(let slug): return "named|\(slug)|"
        case .browse(let path): return "browse|\(path)|"
        case .series(let path): return "series|\(path)|"
        }
    }

    static func decode(_ encoded: String) -> ContentSource? {
        let parts = encoded.split(separator: "|", maxSplits: 2, omittingEmptySubsequences: false).map(String.init)
        guard parts.count >= 2 else { return nil }
        let arg = parts[1]
        let extra = parts.count > 2 ? parts[2] : ""
        switch parts[0] {
        case "api": return .apiCollection(arg, title: extra)
        case "named": return .namedCollection(arg)
        case "browse": return .browse(arg)
        case "series": return .series(arg)
        default: return nil
        }
    }

    static func seriesByCatNum(_ catNum: String) -> ContentSource {
        .series("/series/details?num=\(catNum.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? catNum)")
    }

    /// Value of a query parameter in a path like "/browse?cat=latest&t=audio".
    static func queryValue(_ key: String, in path: String) -> String? {
        guard let q = path.split(separator: "?", maxSplits: 1).dropFirst().first else { return nil }
        for pair in q.split(separator: "&") {
            let kv = pair.split(separator: "=", maxSplits: 1).map(String.init)
            if kv.count == 2, kv[0] == key { return kv[1] }
        }
        return nil
    }
}

/// One page of a list plus what's needed to show its header and fetch the next page.
struct ListPage: Codable {
    static let pageSize = 24

    var items: [SearchResult]
    var totalItems: Int
    var page: Int
    var title: String = ""
    var description: String = ""
    var imageUrl: String = ""
    var hasRemaster: Bool = false
    var omOnly: Bool = false
    /// For browse listings: the API endpoint + query that pages the same list.
    var apiUrl: String = ""
    var apiQuery: String = ""

    var hasMore: Bool { !items.isEmpty && totalItems > 0 && page * ListPage.pageSize < totalItems }
}

/// Content of the website's Digital Legacy page.
struct DigitalLegacy: Codable {
    let title: String
    let description: String
    let sampleCatNum: String
    var seriesPath: String = "/series/details?num=X16"
}
