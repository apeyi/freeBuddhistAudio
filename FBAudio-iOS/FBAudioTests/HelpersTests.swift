import XCTest
@testable import FBAudio

final class HelpersTests: XCTestCase {

    func testSafeFractionPassesThroughValid() {
        XCTAssertEqual(Float(0).safeFraction(), 0)
        XCTAssertEqual(Float(0.5).safeFraction(), 0.5)
        XCTAssertEqual(Float(1).safeFraction(), 1)
    }

    func testSafeFractionCoercesOutOfRange() {
        XCTAssertEqual(Float(-0.3).safeFraction(), 0)
        XCTAssertEqual(Float(1.7).safeFraction(), 1)
    }

    func testSafeFractionMapsNaNAndInfinityToZero() {
        // Values that produce CoreGraphics NaN errors / broken layout if they
        // reach a Slider or ProgressView unguarded.
        XCTAssertEqual(Float.nan.safeFraction(), 0)
        XCTAssertEqual((Float(0) / Float(0)).safeFraction(), 0)
        XCTAssertEqual(Float.infinity.safeFraction(), 0)
        XCTAssertEqual((-Float.infinity).safeFraction(), 0)
        // Double too, since the seek slider uses Double
        XCTAssertEqual(Double.nan.safeFraction(), 0)
    }

    func testFormatDurationMinutesSeconds() {
        XCTAssertEqual(formatDuration(0), "0:00")
        XCTAssertEqual(formatDuration(65), "1:05")
        XCTAssertEqual(formatDuration(600), "10:00")
    }

    func testFormatDurationHours() {
        XCTAssertEqual(formatDuration(3600), "1:00:00")
        XCTAssertEqual(formatDuration(3661), "1:01:01")
        XCTAssertEqual(formatDuration(7200), "2:00:00")
    }

    func testFormatFileSize() {
        XCTAssertEqual(formatFileSize(0), "")
        XCTAssertEqual(formatFileSize(512), "512 B")
        XCTAssertTrue(formatFileSize(1500).contains("KB"))
        XCTAssertTrue(formatFileSize(1_500_000).contains("MB"))
        XCTAssertTrue(formatFileSize(1_500_000_000).contains("GB"))
    }
}
