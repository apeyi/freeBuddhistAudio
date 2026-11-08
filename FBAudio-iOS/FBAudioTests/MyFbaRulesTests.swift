import XCTest
@testable import FBAudio

/// SPEC "My FBA" and "Continue listening (See all)". Parity with Android MyFbaRulesTest.
final class MyFbaRulesTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func entry(_ cat: String, _ posSec: Int, total: Int = 3600, daysAgo: Double = 0, track: Int = 0,
                       speaker: String = "Sangharakshita") -> PersistenceManager.RecentlyListened {
        PersistenceManager.RecentlyListened(catNum: cat, title: cat, speaker: speaker, imageUrl: "",
                                            positionMs: Int64(posSec) * 1000, trackIndex: track,
                                            totalDurationSeconds: total, timestamp: now.addingTimeInterval(-daysAgo * 86_400))
    }

    func testInProgressNeedsAStartAndTimeLeft() {
        XCTAssertFalse(MyFbaRules.isInProgress(entry("a", 2), now: now))
        XCTAssertTrue(MyFbaRules.isInProgress(entry("a", 600), now: now))
        XCTAssertFalse(MyFbaRules.isInProgress(entry("a", 3550), now: now))
        XCTAssertTrue(MyFbaRules.isInProgress(entry("a", 600, total: 0), now: now))
    }

    func testLeavesAfter90DaysWithoutAPlay() {
        XCTAssertTrue(MyFbaRules.isInProgress(entry("a", 600, daysAgo: 89), now: now))
        XCTAssertFalse(MyFbaRules.isInProgress(entry("a", 600, daysAgo: 91), now: now))
    }

    func testContinueListeningIsMostRecentFirst() {
        let list = [entry("old", 600, daysAgo: 5), entry("done", 3599), entry("new", 600, daysAgo: 1)]
        XCTAssertEqual(MyFbaRules.continueListening(list, now: now).map(\.catNum), ["new", "old"])
    }

    func testFinished() {
        XCTAssertTrue(MyFbaRules.isFinished(entry("a", 3550)))
        XCTAssertFalse(MyFbaRules.isFinished(entry("a", 600)))
        XCTAssertFalse(MyFbaRules.isFinished(entry("a", 600, total: 0)))
    }

    func testMeta() {
        XCTAssertEqual(MyFbaRules.continueMeta(entry("a", 600)), "Sangharakshita · 50 min left")
        XCTAssertEqual(MyFbaRules.continueMeta(entry("a", 600, track: 2)), "Sangharakshita · Track 3 · 50 min left")
        XCTAssertEqual(MyFbaRules.continueMeta(entry("a", 600, speaker: "")), "50 min left")
    }

    func testLabels() {
        XCTAssertEqual(MyFbaRules.inProgressLabel(1), "1 talk in progress")
        XCTAssertEqual(MyFbaRules.inProgressLabel(7), "7 talks in progress, most recent first")
        XCTAssertEqual(MyFbaRules.downloadsSummary(count: 0, bytes: 0), "None yet")
        XCTAssertEqual(MyFbaRules.downloadsSummary(count: 1, bytes: 64_400_000), "1 talk · 64 MB")
        XCTAssertEqual(MyFbaRules.downloadsSummary(count: 12, bytes: 1_234_000_000), "12 talks · 1.2 GB")
        XCTAssertEqual(MyFbaRules.formatBytes(80_000), "80 KB")
    }

    func testProgress() {
        XCTAssertEqual(MyFbaRules.progress(entry("a", 1800)), 0.5, accuracy: 0.001)
        XCTAssertEqual(MyFbaRules.progress(entry("a", 1800, total: 0)), 0)
    }

    func testHistoryDayLabels() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let iso = ISO8601DateFormatter()
        let n = iso.date(from: "2027-01-15T08:00:00Z")!
        XCTAssertEqual(MyFbaRules.historyDayLabel(iso.date(from: "2027-01-15T00:10:00Z")!, now: n, calendar: cal), "Today")
        XCTAssertEqual(MyFbaRules.historyDayLabel(iso.date(from: "2027-01-14T23:59:00Z")!, now: n, calendar: cal), "Yesterday")
        XCTAssertEqual(MyFbaRules.historyDayLabel(iso.date(from: "2027-01-11T12:00:00Z")!, now: n, calendar: cal), "Monday 11 January")
        XCTAssertEqual(MyFbaRules.historyDayLabel(iso.date(from: "2026-11-03T12:00:00Z")!, now: n, calendar: cal), "3 November 2026")
    }

    func testSupportRequestCarriesVersionDeviceAndSignIn() {
        let e = ReportProblem.supportRequest(device: "iPhone, iOS 26.0", appVersion: "0.10.0", signedIn: true)
        XCTAssertEqual(e.to, "support@dharmachakra.com")
        XCTAssertEqual(e.subject, "FBA app support")
        XCTAssertTrue(e.body.hasPrefix("\n\n\n"))
        XCTAssertTrue(e.body.contains("App version: 0.10.0"))
        XCTAssertTrue(e.body.contains("Device: iPhone, iOS 26.0"))
        XCTAssertTrue(e.body.contains("Signed in: yes"))
    }
}
