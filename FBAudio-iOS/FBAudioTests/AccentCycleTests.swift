import XCTest
@testable import FBAudio

/// Parity with Android AccentCycleTest.
final class AccentCycleTests: XCTestCase {
    private let london = TimeZone(identifier: "Europe/London")!

    private func at(_ hour: Int, _ minute: Int = 0, zone: TimeZone? = nil) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone ?? london
        return cal.date(from: DateComponents(year: 2026, month: 11, day: 8, hour: hour, minute: minute))!
    }

    func testModesMatchTokensOrderAndStartOnFbaBlue() {
        XCTAssertEqual(AccentCycle.modes.count, 10)
        XCTAssertEqual(AccentCycle.modes.first, .fbaBlue)
        XCTAssertEqual(AccentCycle.defaultMode, .fbaBlue)
        XCTAssertEqual(AccentCycle.modes.last, .fbaPurple)
    }

    func testOneModePerHourWholeHourStable() {
        XCTAssertEqual(AccentCycle.mode(for: at(0)), .fbaBlue)
        XCTAssertEqual(AccentCycle.mode(for: at(0, 59)), .fbaBlue)
        XCTAssertEqual(AccentCycle.mode(for: at(1)), .fbaGreen)
        XCTAssertEqual(AccentCycle.mode(for: at(9)), .fbaPurple)
        XCTAssertEqual(AccentCycle.mode(for: at(10)), .fbaBlue)
        XCTAssertEqual(AccentCycle.mode(for: at(22)), .orderOnlyBlue)
    }

    func testUsesUkTimeWhateverTheDeviceZone() {
        // 09:00 in New York is 14:00 in London → index 4 (Chestnut)
        XCTAssertEqual(AccentCycle.mode(for: at(9, zone: TimeZone(identifier: "America/New_York")!)), .fbaChestnut)
    }

    func testSecondsToNextHour() {
        XCTAssertEqual(AccentCycle.secondsToNextHour(at(3)), 3600, accuracy: 0.001)
        XCTAssertEqual(AccentCycle.secondsToNextHour(at(3, 59)), 60, accuracy: 0.001)
    }

    func testPageThemesOverrideTheHourlyAccent() {
        let hourly = Brand.fbaLilac.set
        XCTAssertEqual(accentFor(.hourly, hourly: hourly), hourly)
        XCTAssertEqual(accentFor(.remastered, hourly: hourly), remasterGold)
        XCTAssertEqual(accentFor(.orderOnly, hourly: hourly), orderOnlyAccent)
        XCTAssertEqual(orderOnlyAccent.deep, 0x006AA3)
        XCTAssertEqual(remasterGold.deep, 0x8A6A1F)
    }
}
