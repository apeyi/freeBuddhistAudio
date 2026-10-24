import XCTest
@testable import FBAudio

/// Exercises the Phase 1 iOS catalogue migration end-to-end against the LIVE API
/// (ContentSource -> V2Catalogue -> generated client -> mapped to the app's
/// ListPage/SearchResult). Runs in Codemagic's test step. Needs network.
final class V2CatalogueLiveTests: XCTestCase {

    override func setUp() {
        super.setUp()
        ApiConfig.install()
    }

    func testLatestMapsToTalks() async throws {
        let page = try await V2Catalogue.getPage(.apiCollection("latest", title: "Latest"), page: 1)
        XCTAssertFalse(page.items.isEmpty, "no items")
        XCTAssertTrue(page.items.first!.isTalk, "first should be a talk")
        XCTAssertGreaterThan(page.totalItems, page.items.count)
        XCTAssertFalse(page.title.isEmpty)
    }

    func testAllSeriesMapsToSeries() async throws {
        let page = try await V2Catalogue.getPage(.apiCollection("all_series", title: "Series"), page: 1)
        XCTAssertFalse(page.items.isEmpty)
        XCTAssertTrue(page.items.first!.isSeries)
    }

    func testBrowseSpeakerMapsToTalks() async throws {
        let page = try await V2Catalogue.getPage(.browse("/browse?s=Sangharakshita"), page: 1)
        XCTAssertFalse(page.items.isEmpty)
        XCTAssertTrue(page.items.allSatisfy { $0.isTalk })
    }

    func testSeriesMembers() async throws {
        let page = try await V2Catalogue.getPage(.seriesByCatNum("X16"), page: 1)
        XCTAssertFalse(page.items.isEmpty)
        XCTAssertTrue(page.items.first!.isTalk)
    }

    func testMenuHasSections() async throws {
        let labels = try await V2Catalogue.getMenu().map { $0.label.lowercased() }
        XCTAssertTrue(labels.contains("people") && labels.contains("places"))
    }

    func testSearch() async throws {
        let talks = try await V2Catalogue.searchTalks("metta")
        XCTAssertFalse(talks.isEmpty)
        let series = try await V2Catalogue.searchSeries("meditation")
        XCTAssertTrue(series.contains { $0.isSeries })
    }

    func testDigitalLegacy() async throws {
        let dl = try await V2Catalogue.getDigitalLegacy()
        XCTAssertFalse(dl.title.isEmpty)
        XCTAssertFalse(dl.sampleCatNum.isEmpty)
    }

    func testSpeakerIndexIsWhole() async throws {
        let speakers = try await V2Catalogue.getIndexEntries("speakers")
        XCTAssertGreaterThan(speakers.count, 300)
        XCTAssertTrue(speakers.first!.isBrowseLink)
    }

    /// An unknown enum value decodes to .unknown instead of failing the response.
    func testEnumLenientDecoding() throws {
        let json = #"""
        {"kind":"podcast","id":"X1","title":"T","subtitle":null,"image":null,"year":null,
         "language":null,"speaker":null,"place":null,"placeName":null,"omOnly":false,
         "talkCount":null,"hasRemaster":null,"webUrl":"https://x"}
        """#.data(using: .utf8)!
        let item = try CodableHelper.jsonDecoder.decode(V2ListItem.self, from: json)
        XCTAssertEqual(item.kind, .unknown)
    }
}
