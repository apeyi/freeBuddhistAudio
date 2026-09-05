import XCTest
@testable import FBAudio

final class CollectionArtworkTests: XCTestCase {

    func testHueIsStableAndInRange() {
        for slug in ["buddha", "meditation-mindfulness", "bodhisattva-ideal", "tara", "arts", "wisdom"] {
            let h = CollectionArtwork.hue(slug)
            XCTAssertEqual(h, CollectionArtwork.hue(slug))
            XCTAssertTrue(h >= 0 && h < 360, "\(slug) → \(h)")
        }
    }

    func testSimilarSlugsGetDifferentHues() {
        XCTAssertNotEqual(CollectionArtwork.hue("tara"), CollectionArtwork.hue("arts"))
        XCTAssertNotEqual(CollectionArtwork.hue("ethics"), CollectionArtwork.hue("wisdom"))
    }

    func testCaseInsensitiveAndEmptySafe() {
        XCTAssertEqual(CollectionArtwork.hue("Buddha"), CollectionArtwork.hue("buddha"))
        XCTAssertEqual(CollectionArtwork.hue(""), 0)
        XCTAssertEqual(CollectionArtwork.secondHue("buddha"), (CollectionArtwork.hue("buddha") + 40).truncatingRemainder(dividingBy: 360))
    }

    /// Same FNV-1a as Android's CollectionArtwork.hue — pinned so the two apps
    /// paint the same collection the same colour.
    func testMatchesAndroidHashForKnownSlug() {
        // Computed with the Android implementation for "buddha".
        var h: UInt32 = 0x811C9DC5
        for c in "buddha".utf16 { h ^= UInt32(c); h = h &* 0x01000193 }
        XCTAssertEqual(CollectionArtwork.hue("buddha"), Double(h % 360))
    }
}
