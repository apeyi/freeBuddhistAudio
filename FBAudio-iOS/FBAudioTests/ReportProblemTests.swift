import XCTest
@testable import FBAudio

/// SPEC "Report a problem sheet". Parity with Android ReportProblemTest.
final class ReportProblemTests: XCTestCase {
    func testReasonsMatchTheDesign() {
        XCTAssertEqual(ReportProblem.reasons, [
            "It won't play, or it stops", "Sound quality", "Wrong title, speaker or details",
            "Problem with the transcript", "Something else",
        ])
    }

    func testEmailCarriesTheTalkPositionAndDevice() {
        let e = ReportProblem.email(reason: "Sound quality", talkTitle: "The Rainbow Body", catNum: "LOC7132",
                                    positionSeconds: 3725, device: "iPhone 16, iOS 26.0", appVersion: "1.0.0", signedIn: true)
        XCTAssertEqual(e.to, "support@dharmachakra.com")
        XCTAssertEqual(e.subject, "Sound quality: The Rainbow Body (LOC7132)")
        XCTAssertTrue(e.body.contains("Talk: The Rainbow Body (LOC7132)"))
        XCTAssertTrue(e.body.contains("Playback position: 1:02:05"))
        XCTAssertTrue(e.body.contains("Device: iPhone 16, iOS 26.0"))
        XCTAssertTrue(e.body.contains("Signed in: yes"))
        XCTAssertFalse(e.body.contains("—"))
        XCTAssertNotNil(ReportProblem.mailtoURL(e))
    }

    func testNoPositionWhenNotPlaying() {
        let e = ReportProblem.email(reason: "Something else", talkTitle: "T", catNum: "1", positionSeconds: nil,
                                    device: "d", appVersion: "v", signedIn: false)
        XCTAssertFalse(e.body.contains("Playback position"))
        XCTAssertEqual(ReportProblem.formatClock(662), "11:02")
    }
}
