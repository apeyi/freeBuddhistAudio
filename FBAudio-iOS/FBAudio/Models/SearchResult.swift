import Foundation

struct SearchResult: Identifiable, Equatable, Codable {
    let catNum: String
    let title: String
    let speaker: String
    let imageUrl: String
    let path: String
    let year: Int
    /// Centre / place the talk was recorded at (used by the language filter).
    let centre: String
    let omOnly: Bool

    // Includes the path: series and talk numbers are separate namespaces on FBA,
    // so two results can legitimately share a catNum (duplicate ids break ForEach).
    var id: String { "\(path)|\(catNum)" }

    var isSeries: Bool { path.contains("/series/") }
    /// Speaker / place / year / genre entries link to a browse listing, not a talk.
    var isBrowseLink: Bool { path.contains("/browse") }
    /// Curated `/collection/<slug>` pages (themes, collections).
    var isCollection: Bool { path.contains("/collection/") }
    var isTalk: Bool { !isSeries && !isBrowseLink && !isCollection }

    init(catNum: String, title: String = "", speaker: String = "",
         imageUrl: String = "", path: String = "", year: Int = 0,
         centre: String = "", omOnly: Bool = false) {
        self.catNum = catNum
        self.title = title
        self.speaker = speaker
        self.imageUrl = imageUrl
        self.path = path
        self.year = year
        self.centre = centre
        self.omOnly = omOnly
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        catNum = try c.decode(String.self, forKey: .catNum)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        speaker = try c.decodeIfPresent(String.self, forKey: .speaker) ?? ""
        imageUrl = try c.decodeIfPresent(String.self, forKey: .imageUrl) ?? ""
        path = try c.decodeIfPresent(String.self, forKey: .path) ?? ""
        year = try c.decodeIfPresent(Int.self, forKey: .year) ?? 0
        centre = try c.decodeIfPresent(String.self, forKey: .centre) ?? ""
        omOnly = try c.decodeIfPresent(Bool.self, forKey: .omOnly) ?? false
    }
}
