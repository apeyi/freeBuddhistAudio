import XCTest
@testable import FBAudio

/// SPEC "Tile artwork". Parity with Android TileArtTest and FeaturedLinkTest.
final class TileArtTests: XCTestCase {
    func testHomeTilesInDesignOrderPeopleIsABuddha() {
        XCTAssertEqual(TileArt.homeTiles.map(\.title), ["Introducing Buddhism", "Meditation & mindfulness",
            "Latest talks & meditations", "Themes", "Series", "People", "Places", "Order talks"])
        XCTAssertEqual(TileArt.homeTiles.first { $0.title == "People" }?.symbol, .buddha)
    }

    func testMainNeverBlendsIntoTheSameColour() {
        for c in TileArt.homeTiles.map(\.colors) + TileArt.rainbow + TileArt.symbolSafe { XCTAssertNotEqual(c.main, c.partner) }
    }

    func testYellowIsNeverMainAndNeverNextToASymbol() {
        for c in TileArt.rainbow { XCTAssertNotEqual(c.main, TileArt.yellow) }
        for c in TileArt.symbolSafe + TileArt.homeTiles.map(\.colors) {
            XCTAssertFalse([c.main, c.partner, c.glow].contains(TileArt.yellow))
        }
    }

    func testRainbowStepsAndWraps() {
        XCTAssertEqual(TileArt.rainbowAt(0, start: 3), TileArt.rainbow[3])
        XCTAssertEqual(TileArt.rainbowAt(7, start: 3), TileArt.rainbow[0])
        for i in 0..<20 { XCTAssertNotEqual(TileArt.rainbowAt(i, start: 5), TileArt.rainbowAt(i + 1, start: 5)) }
    }

    func testSymbolPathsParse() {
        for s in TileSymbol.allCases {
            XCTAssertFalse(s.shape.path(in: CGRect(x: 0, y: 0, width: 100, height: 100)).isEmpty, "\(s)")
        }
    }

    func testFeaturedLinksRoute() {
        XCTAssertEqual(FeaturedLink.target("https://www.freebuddhistaudio.com/series/details?num=X01"), .series("X01"))
        XCTAssertEqual(FeaturedLink.target("https://www.freebuddhistaudio.com/audio/details?num=LOC6479"), .talk("LOC6479"))
        XCTAssertEqual(FeaturedLink.target("https://www.freebuddhistaudio.com/digital-legacy"), .digitalLegacy)
        XCTAssertEqual(FeaturedLink.target("https://www.freebuddhistaudio.com/about"), .web("https://www.freebuddhistaudio.com/about"))
    }
}
