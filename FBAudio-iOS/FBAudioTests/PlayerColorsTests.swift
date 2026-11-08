import XCTest
@testable import FBAudio

/// Full-screen player colour and sleep timer. Parity with Android PlayerColorsTest.
final class PlayerColorsTests: XCTestCase {
    func testColourfulArtworkGivesStrongestOnTopDeeperBelow() {
        let g = PlayerColors.fromColors([0xE0592B, 0x264A6C])
        XCTAssertNotNil(g)
        XCTAssertLessThan(PlayerColors.luminance(g!.bottom), PlayerColors.luminance(g!.top))
    }

    func testPrefersAStrongColourOverPaleYellowAndContrastsTheHue() {
        let g = PlayerColors.fromColors([0xE8C547, 0xC8452A, 0x2F5B87])!
        let top = PlayerColors.hue(g.top)
        XCTAssertTrue(top < 30 || top > 340, "top should be red-orange, got \(top)")
        XCTAssertTrue((190...250).contains(PlayerColors.hue(g.bottom)), "bottom should be blue")
    }

    func testBlackAndWhiteArtworkFallsBackToTheHourlyColour() {
        XCTAssertNil(PlayerColors.fromColors([0x808080, 0x303030]))
        XCTAssertNil(PlayerColors.fromColors([]))
        XCTAssertTrue(ArtworkPalette.colors(pixels: Array(repeating: 0x777777, count: 100)).isEmpty)
    }

    func testWhiteTextIsAlwaysReadable() {
        let cases = [PlayerColors.fromColors([0xFFE680])!, PlayerColors.forAccent(Brand.fbaBlue.set),
                     PlayerColors.forAccent(Brand.fbaLilac.set), PlayerColors.forGold()]
        for g in cases {
            XCTAssertGreaterThanOrEqual(PlayerColors.contrastWithWhite(g.top), 3.0)
            XCTAssertGreaterThanOrEqual(PlayerColors.contrastWithWhite(g.bottom), 4.5)
        }
    }

    func testArtworkPaletteGroupsByHueMostCommonFirst() {
        let pixels = Array(repeating: UInt32(0xC8452A), count: 60) + Array(repeating: UInt32(0x2F5B87), count: 30)
            + Array(repeating: UInt32(0x808080), count: 10)
        let colors = ArtworkPalette.colors(pixels: pixels)
        XCTAssertEqual(colors.count, 2)
        XCTAssertEqual(colors.first, 0xC8452A)
    }

    func testSleepFadeIsFullThenLinear() {
        XCTAssertEqual(SleepFade.volume(remaining: 60), 1)
        XCTAssertEqual(SleepFade.volume(remaining: 10), 1)
        XCTAssertEqual(SleepFade.volume(remaining: 5), 0.5, accuracy: 0.001)
        XCTAssertEqual(SleepFade.volume(remaining: 0), 0)
    }

    func testSleepOptionsMatchTheDesign() {
        XCTAssertEqual(SleepOption.allCases.map(\.label),
                       ["Off", "5 minutes", "15 minutes", "30 minutes", "45 minutes", "1 hour",
                        "End of this track", "End of this talk"])
        XCTAssertEqual(SleepOption.min15.shortLabel, "15 min")
        XCTAssertEqual(SleepOption.hour1.shortLabel, "1 hr")
    }
}
