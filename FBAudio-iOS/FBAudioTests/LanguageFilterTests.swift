import XCTest
@testable import FBAudio

final class LanguageFilterTests: XCTestCase {

    private let menu: [MenuNode] = [
        MenuNode(label: "people", link: "#", children: [
            MenuNode(label: "abhaya", link: "/browse?s=Abhaya"),
            MenuNode(label: "amalamati (en español)", link: "/browse?s=Amalamati"),
            MenuNode(label: "dharmadipa (deutsch | english)", link: "/browse?s=Dharmadipa"),
            MenuNode(label: "chandrabodhi (हिंदी में)", link: "/browse?s=Chandrabodhi"),
        ]),
        MenuNode(label: "places", link: "#", children: [
            MenuNode(label: "barcelona (españa)", link: "/browse?p=Triratna_Barcelona"),
            MenuNode(label: "cambridge (england)", link: "/browse?p=Cambridge"),
            MenuNode(label: "nagpur (भारत | india)", link: "/browse?p=Nagpur"),
            MenuNode(label: "london (uk)", link: "#", children: [MenuNode(label: "talks from north london", link: "/browse?p=North_London")]),
        ]),
        MenuNode(label: "languages", link: "#languages", children: [
            MenuNode(label: "лекции на русском языке", link: "/browse?s=Suvannavira"),
            MenuNode(label: "forelesninger på norsk", link: "/browse?p=Oslo"),
        ]),
    ]

    func testLabelMarkersDetectNonEnglishButNotBilingual() {
        XCTAssertTrue(LanguageFilter.isNonEnglishLabel("amalamati (en español)"))
        XCTAssertTrue(LanguageFilter.isNonEnglishLabel("Bodhisattva Ideal (deutsch)"))
        XCTAssertTrue(LanguageFilter.isNonEnglishLabel("chandrabodhi (हिंदी में)"))
        XCTAssertFalse(LanguageFilter.isNonEnglishLabel("dharmadipa (deutsch | english)"))
        XCTAssertFalse(LanguageFilter.isNonEnglishLabel("abhaya"))
        XCTAssertFalse(LanguageFilter.isNonEnglishLabel("The Buddha's Noble Eightfold Path"))
    }

    func testPlaceLabelsDetectNonEnglishCountriesButLeaveIndiaVisible() {
        XCTAssertTrue(LanguageFilter.isNonEnglishPlaceLabel("barcelona (españa)"))
        XCTAssertTrue(LanguageFilter.isNonEnglishPlaceLabel("essen (deutschland)"))
        XCTAssertFalse(LanguageFilter.isNonEnglishPlaceLabel("nagpur (भारत | india)"))
        XCTAssertFalse(LanguageFilter.isNonEnglishPlaceLabel("cambridge (england)"))
    }

    func testSpeakersAndCentresAreDerivedFromTheMenu() {
        XCTAssertEqual(LanguageFilter.nonEnglishSpeakers(menu), ["amalamati", "chandrabodhi", "suvannavira"])
        let centres = LanguageFilter.nonEnglishCentres(menu)
        XCTAssertTrue(centres.contains("triratna barcelona"))
        XCTAssertTrue(centres.contains("barcelona"))
        XCTAssertTrue(centres.contains("oslo"))
        XCTAssertFalse(centres.contains("cambridge"))
        XCTAssertFalse(centres.contains("nagpur"))
    }

    func testFilterMenuHidesMarkedEntriesRecursively() {
        let filtered = LanguageFilter.filterMenu(menu[0].children, englishOnly: true)
        XCTAssertEqual(filtered.map(\.label), ["abhaya", "dharmadipa (deutsch | english)"])
        XCTAssertEqual(LanguageFilter.filterMenu(menu[0].children, englishOnly: false).count, 4)
    }

    func testFilterItemsUsesSpeakerCentreAndTitleMarkers() {
        let items = [
            SearchResult(catNum: "1", title: "The Revealer of Treasures", speaker: "Vajrashura", path: "/audio/details?num=1", centre: "Dublin"),
            SearchResult(catNum: "2", title: "El mantra de Padmasambhava", speaker: "Amalamati", path: "/audio/details?num=2", centre: "Valencia"),
            SearchResult(catNum: "3", title: "Nacido del loto", speaker: "Silamani", path: "/audio/details?num=3", centre: "Triratna Barcelona"),
            SearchResult(catNum: "4", title: "Achtsamkeit (deutsch)", speaker: "Someone", path: "/audio/details?num=4"),
        ]
        let speakers = LanguageFilter.nonEnglishSpeakers(menu)
        let centres = LanguageFilter.nonEnglishCentres(menu)
        let kept = LanguageFilter.filterItems(items, englishOnly: true, nonEnglishSpeakers: speakers, nonEnglishCentres: centres)
        XCTAssertEqual(kept.map(\.catNum), ["1"])
        XCTAssertEqual(LanguageFilter.filterItems(items, englishOnly: false, nonEnglishSpeakers: speakers, nonEnglishCentres: centres).count, 4)
    }

    func testLinkHelpersDecodeNames() {
        XCTAssertEqual(LanguageFilter.speakerFromLink("/browse?s=Vajragupta_(m)&t=audio"), "vajragupta (m)")
        XCTAssertEqual(LanguageFilter.placeFromLink("/browse?p=Triratna_Barcelona"), "triratna barcelona")
        XCTAssertNil(LanguageFilter.speakerFromLink("/collection/subhuti"))
    }
}
