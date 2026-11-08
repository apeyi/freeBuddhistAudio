import XCTest
@testable import FBAudio

/// SPEC "Talk artwork: which image to show". Parity with Android ArtworkRuleTest.
final class ArtworkRuleTests: XCTestCase {
    func testBannerThenSpeakerThenListImageThenVarious() {
        XCTAssertEqual(ArtworkRule.talkImage(bannerUrl: "b", speakerImageUrl: "s", listImageUrl: "l"), "b")
        XCTAssertEqual(ArtworkRule.talkImage(bannerUrl: "", speakerImageUrl: "s", listImageUrl: "l"), "s")
        XCTAssertEqual(ArtworkRule.talkImage(bannerUrl: nil, speakerImageUrl: "s", listImageUrl: "l"), "s")
        XCTAssertEqual(ArtworkRule.talkImage(bannerUrl: " ", speakerImageUrl: nil, listImageUrl: "l"), "l")
        XCTAssertNil(ArtworkRule.talkImage(bannerUrl: nil, speakerImageUrl: "", listImageUrl: nil))
    }

    func testSitePlaceholdersCountAsMissing() {
        XCTAssertEqual(ArtworkRule.talkImage(bannerUrl: "https://x/images/default.jpg", speakerImageUrl: "s", listImageUrl: nil), "s")
    }

    func testDetectsPreCutCirclesOnTransparentOrWhite() {
        func circle(blankOutside: Bool) -> (Int, Int) -> Bool {
            { x, y in
                let dx = Double(x - 250), dy = Double(y - 250)
                return dx * dx + dy * dy <= 250 * 250 ? false : blankOutside
            }
        }
        XCTAssertTrue(ArtworkRule.looksPreCutCircle(width: 500, height: 500, blank: circle(blankOutside: true)))
        XCTAssertFalse(ArtworkRule.looksPreCutCircle(width: 500, height: 500, blank: { x, y in x < 20 && y < 20 }))
        XCTAssertFalse(ArtworkRule.looksPreCutCircle(width: 500, height: 500, blank: { _, _ in true }))
        XCTAssertTrue(ArtworkRule.isBlank(r: 250, g: 250, b: 250, a: 255))
        XCTAssertTrue(ArtworkRule.isBlank(r: 0, g: 0, b: 0, a: 0))
        XCTAssertFalse(ArtworkRule.isBlank(r: 138, g: 106, b: 79, a: 255))
    }

    func testInscribedCropFitsInsideTheCircle() {
        let c = ArtworkRule.inscribedCrop(width: 500, height: 500, aspect: 362.0 / 240.0)
        XCTAssertEqual(c.width, 416)
        XCTAssertEqual(c.height, 276)
        XCTAssertEqual(c.x, (500 - 416) / 2)
        XCTAssertEqual(c.y, (500 - 276) / 2)
        XCTAssertEqual(ArtworkRule.inscribedCrop(width: 500, height: 500, aspect: 1).width, 353)
    }
}
