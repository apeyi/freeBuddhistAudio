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

    func testInscribedCropFitsInsideTheCircle() {
        let c = ArtworkRule.inscribedCrop(width: 500, height: 500, aspect: 362.0 / 240.0)
        XCTAssertEqual(c.width, 416)
        XCTAssertEqual(c.height, 276)
        XCTAssertEqual(c.x, (500 - 416) / 2)
        XCTAssertEqual(c.y, (500 - 276) / 2)
        XCTAssertEqual(ArtworkRule.inscribedCrop(width: 500, height: 500, aspect: 1).width, 353)
    }
}
