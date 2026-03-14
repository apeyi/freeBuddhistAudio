import Foundation

/// Loads shared JSON data files bundled from fbaudio-shared/
class SharedDataLoader {

    struct SangharakshitaJSON: Codable {
        let speaker: String
        let talks: [SangTalk]
        let series: [SangSeries]
    }

    struct SangTalk: Codable {
        let catNum: String
        let title: String
        let year: Int
        let imageUrl: String
    }

    struct SangSeries: Codable {
        let id: String
        let title: String
    }

    struct MitraStudyJSON: Codable {
        let modules: [MitraModuleJSON]
    }

    struct MitraModuleJSON: Codable {
        let id: String
        let name: String
        let year: Int
        let seriesCodes: [String]
        let talks: [MitraTalkJSON]
    }

    struct MitraTalkJSON: Codable {
        let catNum: String
        let title: String
        let speaker: String
        let imageUrl: String
    }

    // MARK: - Sangharakshita

    private static var _sangharakshitaTalks: [SearchResult]?
    private static var _sangharakshitaSeries: [SangSeries]?

    private static func fixTitle(_ title: String) -> String {
        if title.hasSuffix(", The") {
            return "The " + title.dropLast(5)
        } else if title.hasSuffix(", A") {
            return "A " + title.dropLast(3)
        } else if title.hasSuffix(", An") {
            return "An " + title.dropLast(4)
        }
        return title
    }

    private static func loadSangharakshita() {
        guard let url = Bundle.main.url(forResource: "sangharakshita", withExtension: "json", subdirectory: "fbaudio-shared") else {
            print("sangharakshita.json not found in bundle")
            return
        }
        guard let data = try? Data(contentsOf: url),
              let json = try? JSONDecoder().decode(SangharakshitaJSON.self, from: data) else {
            print("Failed to decode sangharakshita.json")
            return
        }
        _sangharakshitaTalks = json.talks.map { talk in
            SearchResult(
                catNum: talk.catNum,
                title: fixTitle(talk.title),
                speaker: "Sangharakshita",
                imageUrl: talk.imageUrl,
                path: "https://www.freebuddhistaudio.com/audio/details?num=\(talk.catNum)",
                year: talk.year
            )
        }
        _sangharakshitaSeries = json.series
    }

    static var sangharakshitaTalks: [SearchResult] {
        if _sangharakshitaTalks == nil { loadSangharakshita() }
        return _sangharakshitaTalks ?? []
    }

    static var sangharakshitaSeries: [SangSeries] {
        if _sangharakshitaSeries == nil { loadSangharakshita() }
        return _sangharakshitaSeries ?? []
    }

    static func sangharakshitaSeriesAsCategories() -> [BrowseCategory] {
        sangharakshitaSeries.map { s in
            BrowseCategory(
                id: "sang_series_\(s.id)",
                name: s.title,
                type: .series,
                browseUrl: "https://www.freebuddhistaudio.com/series/details?num=\(s.id)"
            )
        }
    }

    // MARK: - Mitra Study

    struct MitraModule: Identifiable {
        let id: String
        let name: String
        let year: Int
        let seriesCodes: [String]
        let talks: [MitraTalk]
    }

    struct MitraTalk {
        let catNum: String
        let title: String
        let speaker: String
        let imageUrl: String
    }

    private static var _mitraModules: [MitraModule]?

    private static func loadMitraStudy() {
        guard let url = Bundle.main.url(forResource: "mitra_study", withExtension: "json", subdirectory: "fbaudio-shared") else {
            print("mitra_study.json not found in bundle")
            return
        }
        guard let data = try? Data(contentsOf: url),
              let json = try? JSONDecoder().decode(MitraStudyJSON.self, from: data) else {
            print("Failed to decode mitra_study.json")
            return
        }
        _mitraModules = json.modules.map { m in
            MitraModule(
                id: m.id,
                name: m.name,
                year: m.year,
                seriesCodes: m.seriesCodes,
                talks: m.talks.map { t in
                    MitraTalk(catNum: t.catNum, title: t.title, speaker: t.speaker, imageUrl: t.imageUrl)
                }
            )
        }
    }

    static var mitraModules: [MitraModule] {
        if _mitraModules == nil { loadMitraStudy() }
        return _mitraModules ?? []
    }

    static func modulesByYear() -> [Int: [MitraModule]] {
        Dictionary(grouping: mitraModules, by: { $0.year })
    }

    static func moduleTalksAsSearchResults(_ moduleId: String) -> [SearchResult] {
        guard let module = mitraModules.first(where: { $0.id == moduleId }) else { return [] }
        return module.talks.map { talk in
            SearchResult(
                catNum: talk.catNum,
                title: talk.title,
                speaker: talk.speaker,
                imageUrl: talk.imageUrl,
                path: "https://www.freebuddhistaudio.com/audio/details?num=\(talk.catNum)"
            )
        }
    }

    static func yearCategories() -> [BrowseCategory] {
        (1...4).map { year in
            BrowseCategory(
                id: "mitra_year_\(year)",
                name: "Year \(year)",
                type: .mitraYear,
                browseUrl: "mitra://year/\(year)"
            )
        }
    }

    static func moduleCategories(year: Int) -> [BrowseCategory] {
        mitraModules.filter { $0.year == year }.map { module in
            BrowseCategory(
                id: module.id,
                name: module.name,
                type: .mitraModule,
                browseUrl: "mitra://module/\(module.id)"
            )
        }
    }
}
