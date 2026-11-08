import XCTest
@testable import FBAudio

/// SPEC "Talk page, Resume (partly listened)". Parity with Android TalkProgressTest.
final class TalkProgressTests: XCTestCase {
    private func track(_ seconds: Int) -> Track { Track(title: "", durationSeconds: seconds, audioUrl: "") }
    private var tracks: [Track] { [track(3649), track(1746)] }   // LOC7132
    private let total = 5395

    func testNeverStartedShowsPlay() {
        XCTAssertNil(TalkProgress.resume(tracks: tracks, totalSeconds: total, savedTrack: 0, savedPositionMs: 0))
        XCTAssertNil(TalkProgress.resume(tracks: tracks, totalSeconds: total, savedTrack: 0, savedPositionMs: 3_000))
    }

    func testPartlyHeardResumes() {
        let r = TalkProgress.resume(tracks: tracks, totalSeconds: total, savedTrack: 1, savedPositionMs: 662_000)
        XCTAssertEqual(r?.trackIndex, 1)
        XCTAssertEqual(r?.remainingSeconds, 1084)
        XCTAssertEqual(r.map { TalkProgress.resumeLabel($0, trackCount: 2) }, "Track 2 · 19 min left")
        XCTAssertEqual(r.map { TalkProgress.resumeLabel($0, trackCount: 1) }, "19 min left")
    }

    func testWithinTheLastMinuteCountsAsListened() {
        XCTAssertNil(TalkProgress.resume(tracks: tracks, totalSeconds: total, savedTrack: 1, savedPositionMs: Int64(1746 - 30) * 1000))
        XCTAssertEqual(TalkProgress.resume(tracks: tracks, totalSeconds: total, savedTrack: 1, savedPositionMs: Int64(1746 - 90) * 1000)?.remainingSeconds, 90)
    }

    func testTrackStates() {
        let r = TalkProgress.resume(tracks: tracks, totalSeconds: total, savedTrack: 1, savedPositionMs: 662_000)
        XCTAssertEqual(TalkProgress.trackState(0, r), .listened)
        XCTAssertEqual(TalkProgress.trackState(1, r), .inProgress)
        XCTAssertEqual(TalkProgress.trackState(2, r), .notStarted)
        XCTAssertEqual(TalkProgress.trackState(0, nil), .notStarted)
    }

    func testTrackFractionAndRemaining() {
        XCTAssertEqual(TalkProgress.trackFraction(track(600), positionMs: 300_000), 0.5, accuracy: 0.0001)
        XCTAssertEqual(TalkProgress.trackFraction(track(0), positionMs: 300_000), 0, accuracy: 0.0001)
        XCTAssertEqual(TalkProgress.trackFraction(track(600), positionMs: 900_000), 1, accuracy: 0.0001)
        XCTAssertEqual(TalkProgress.trackRemainingSeconds(track(1746), positionMs: 662_000), 1084)
    }

    func testOutOfRangeSavedTrackIsClamped() {
        XCTAssertEqual(TalkProgress.resume(tracks: tracks, totalSeconds: total, savedTrack: 7, savedPositionMs: 10_000)?.trackIndex, 1)
    }

    func testLengthLabelMatchesTheDesign() {
        XCTAssertEqual(TalkProgress.lengthLabel(59 * 60), "59 min")
        XCTAssertEqual(TalkProgress.lengthLabel(5395), "90 min")
        XCTAssertEqual(TalkProgress.lengthLabel(7200), "2 hr")
        XCTAssertEqual(TalkProgress.lengthLabel(8 * 3600 + 14 * 60), "8 hr 14 min")
    }
}
