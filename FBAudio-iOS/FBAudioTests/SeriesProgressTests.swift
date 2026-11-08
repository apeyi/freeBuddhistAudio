import XCTest
@testable import FBAudio

/// SPEC "Series page, Resume". Parity with Android SeriesProgressTest.
final class SeriesProgressTests: XCTestCase {
    private let members = ["A", "B", "C", "D"]
    private func done(_ at: TimeInterval) -> SeriesProgress.Saved { .init(positionMs: 3_540_000, totalSeconds: 3600, listenedAt: Date(timeIntervalSince1970: at)) }
    private func partly(_ pos: Int64, _ at: TimeInterval) -> SeriesProgress.Saved { .init(positionMs: pos, totalSeconds: 3600, listenedAt: Date(timeIntervalSince1970: at)) }

    func testNothingHeardShowsPlayAll() {
        XCTAssertNil(SeriesProgress.resume(members: members, saved: [:]))
        XCTAssertNil(SeriesProgress.resume(members: members, saved: ["A": partly(2_000, 1)]))
    }

    func testResumesTheTalkInProgress() {
        let r = SeriesProgress.resume(members: members, saved: ["A": done(1), "B": done(2), "C": partly(1_740_000, 3)])
        XCTAssertEqual(r, SeriesProgress.Resume(index: 2, catNum: "C", remainingSeconds: 1860))
    }

    func testAfterAFinishedTalkResumesTheNextOne() {
        XCTAssertEqual(SeriesProgress.resume(members: members, saved: ["A": done(1), "B": done(2)])?.index, 2)
    }

    func testMostRecentPlayWins() {
        XCTAssertEqual(SeriesProgress.resume(members: members, saved: ["D": partly(600_000, 1), "A": partly(300_000, 5)])?.catNum, "A")
    }

    func testEverythingFinishedShowsPlayAll() {
        XCTAssertNil(SeriesProgress.resume(members: members, saved: Dictionary(uniqueKeysWithValues: members.map { ($0, done(1)) })))
    }
}
