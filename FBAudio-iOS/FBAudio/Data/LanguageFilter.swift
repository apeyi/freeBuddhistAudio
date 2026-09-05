import Foundation

/// "English | All languages" support built on the markers FBA already maintains
/// (curated menu labels, the Languages section, Places country labels). The
/// search API's language code is NOT used — it is unreliable. India is left
/// visible: talks there are in English or Hindi. Mirrors Android's LanguageFilter.
enum LanguageFilter {

    private static let languageMarkers = [
        "deutsch", "español", "espanol", "nederlands", "svenska", "français", "francais",
        "हिंदी", "polsku", "português", "portugues", "norsk", "русском", "suomeksi",
        "italiano", "auf deutsch", "en français",
    ]

    private static let nonEnglishCountryMarkers = [
        "españa", "espana", "deutschland", "méxico", "mexico", "nederland", "vlaams",
        "france", "norge", "sverige", "brasil", "россия", "krakow", "kraków", "polska",
    ]

    /// Does a curated menu label mark a non-English entry (and not a bilingual one)?
    static func isNonEnglishLabel(_ label: String) -> Bool {
        let l = label.lowercased()
        if l.contains("english") { return false }
        return languageMarkers.contains { l.contains($0) }
    }

    /// Does a Places label name a non-English-speaking country?
    static func isNonEnglishPlaceLabel(_ label: String) -> Bool {
        let l = label.lowercased()
        return nonEnglishCountryMarkers.contains { l.contains($0) }
    }

    /// Speaker names (lowercase) marked non-English in the People and Languages sections.
    static func nonEnglishSpeakers(_ menu: [MenuNode]) -> Set<String> {
        var out = Set<String>()
        for node in SiteMenuParser.section(menu, "people")?.children ?? [] where isNonEnglishLabel(node.label) {
            if let s = speakerFromLink(node.link) { out.insert(s) }
        }
        for node in SiteMenuParser.section(menu, "languages")?.children ?? [] {
            if let s = speakerFromLink(node.link) { out.insert(s) }
        }
        return out
    }

    /// Centre names (lowercase) from Places entries in non-English-speaking countries.
    static func nonEnglishCentres(_ menu: [MenuNode]) -> Set<String> {
        var out = Set<String>()
        func visit(_ node: MenuNode) {
            if isNonEnglishPlaceLabel(node.label) {
                if let p = placeFromLink(node.link) { out.insert(p) }
                let city = node.label.split(separator: "(").first.map { String($0).trimmingCharacters(in: .whitespaces).lowercased() } ?? ""
                if !city.isEmpty { out.insert(city) }
            }
            node.children.forEach(visit)
        }
        (SiteMenuParser.section(menu, "places")?.children ?? []).forEach(visit)
        for node in SiteMenuParser.section(menu, "languages")?.children ?? [] {
            if let p = placeFromLink(node.link) { out.insert(p) }
        }
        return out
    }

    /// Filter menu entries for the English-only setting.
    static func filterMenu(_ nodes: [MenuNode], englishOnly: Bool) -> [MenuNode] {
        guard englishOnly else { return nodes }
        return nodes.filter { !isNonEnglishLabel($0.label) }.map { node in
            node.hasChildren
                ? MenuNode(label: node.label, link: node.link, om: node.om, children: filterMenu(node.children, englishOnly: true))
                : node
        }
    }

    /// Filter talk/series items for the English-only setting.
    static func filterItems(_ items: [SearchResult], englishOnly: Bool,
                            nonEnglishSpeakers: Set<String>, nonEnglishCentres: Set<String>) -> [SearchResult] {
        guard englishOnly else { return items }
        return items.filter { item in
            let speaker = item.speaker.trimmingCharacters(in: .whitespaces).lowercased()
            let centre = item.centre.trimmingCharacters(in: .whitespaces).lowercased()
            if !speaker.isEmpty && nonEnglishSpeakers.contains(speaker) { return false }
            if !centre.isEmpty && nonEnglishCentres.contains(where: { centre == $0 || centre.contains($0) }) { return false }
            if isNonEnglishLabel(item.title) { return false }
            return true
        }
    }

    /// "/browse?s=Amalamati" → "amalamati"
    static func speakerFromLink(_ link: String) -> String? {
        decodedQuery("s", link)
    }

    /// "/browse?p=Triratna_Barcelona" → "triratna barcelona"
    static func placeFromLink(_ link: String) -> String? {
        decodedQuery("p", link)
    }

    private static func decodedQuery(_ key: String, _ link: String) -> String? {
        guard let raw = ContentSource.queryValue(key, in: link) else { return nil }
        let withoutFragment = raw.split(separator: "#").first.map(String.init) ?? raw
        let decoded = withoutFragment.replacingOccurrences(of: "+", with: "%2B").removingPercentEncoding ?? withoutFragment
        return decoded.replacingOccurrences(of: "_", with: " ").trimmingCharacters(in: .whitespaces).lowercased()
    }
}
