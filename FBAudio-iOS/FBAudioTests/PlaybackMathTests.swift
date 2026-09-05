import XCTest
@testable import FBAudio

final class PlaybackMathTests: XCTestCase {

    private func track(_ seconds: Int) -> Track {
        Track(title: "", durationSeconds: seconds, audioUrl: "")
    }

    // MARK: cumulativePositionMs

    func testCumulativeSingleTrackIsInTrackPosition() {
        XCTAssertEqual(PlaybackMath.cumulativePositionMs(tracks: [], trackIndex: 0, positionInTrackMs: 30_000), 30_000)
    }

    func testCumulativeAddsPriorTracks() {
        let tracks = [track(60), track(120), track(90)]
        // Track 2, 45s in: 60 + 120 prior + 45 = 225s
        XCTAssertEqual(PlaybackMath.cumulativePositionMs(tracks: tracks, trackIndex: 2, positionInTrackMs: 45_000), 225_000)
    }

    func testCumulativeClampsNegativeAndOutOfRange() {
        let tracks = [track(60), track(60)]
        XCTAssertEqual(PlaybackMath.cumulativePositionMs(tracks: tracks, trackIndex: 99, positionInTrackMs: -5), 120_000)
    }

    // MARK: totalDurationSeconds

    func testTotalPrefersTalkMetadata() {
        XCTAssertEqual(PlaybackMath.totalDurationSeconds(talkDurationSeconds: 3600, tracks: [track(10)], playerDurationMs: 999_000), 3600)
    }

    func testTotalFallsBackToSummedTracks() {
        XCTAssertEqual(PlaybackMath.totalDurationSeconds(talkDurationSeconds: 0, tracks: [track(60), track(120)], playerDurationMs: 999_000), 180)
    }

    func testTotalFallsBackToPlayerDurationWhenTracksHaveNoDurations() {
        // "Jewel in the Lotus": 16 chapters, all durationSeconds == 0.
        let tracks = Array(repeating: track(0), count: 16)
        XCTAssertEqual(PlaybackMath.totalDurationSeconds(talkDurationSeconds: 0, tracks: tracks, playerDurationMs: 95_000), 95)
    }

    func testTotalIsZeroOnlyWhenNothingKnown() {
        XCTAssertEqual(PlaybackMath.totalDurationSeconds(talkDurationSeconds: 0, tracks: [], playerDurationMs: 0), 0)
    }

    // MARK: clampPosition

    func testClampPositionKeepsTimeWithinNewVersion() {
        XCTAssertEqual(PlaybackMath.clampPosition(positionMs: 120_000, newDurationMs: 760_000), 120_000)
        // Remastered track is 9 s shorter than where we were → land just before its end
        XCTAssertEqual(PlaybackMath.clampPosition(positionMs: 768_000, newDurationMs: 760_000), 759_000)
        XCTAssertEqual(PlaybackMath.clampPosition(positionMs: 50_000, newDurationMs: nil), 50_000)
        XCTAssertEqual(PlaybackMath.clampPosition(positionMs: 50_000, newDurationMs: 0), 50_000)
        XCTAssertEqual(PlaybackMath.clampPosition(positionMs: -5, newDurationMs: 10_000), 0)
    }

    func testAbsurdTalkDurationFallsBackToTracks() {
        let tracks = [track(1200), track(1800)]
        // LOC3883 on the website: 717,860,544 seconds
        XCTAssertEqual(PlaybackMath.totalDurationSeconds(talkDurationSeconds: 717_860_544, tracks: tracks, playerDurationMs: 0), 3000)
        XCTAssertEqual(PlaybackMath.totalDurationSeconds(talkDurationSeconds: 717_860_544, tracks: [], playerDurationMs: 0), 0)
        XCTAssertEqual(PlaybackMath.totalDurationSeconds(talkDurationSeconds: -5, tracks: [], playerDurationMs: 42_000), 42)
        XCTAssertTrue(PlaybackMath.isPlausibleDuration(20 * 3600))
        XCTAssertFalse(PlaybackMath.isPlausibleDuration(0))
    }
}
