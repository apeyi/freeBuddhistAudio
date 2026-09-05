import XCTest
@testable import FBAudio

final class FuzzyMatchTests: XCTestCase {

    func testSubstringAlwaysMatches() {
        XCTAssertTrue(FuzzyMatch.matches("lbc", "talks from east london (lbc)"))
        XCTAssertTrue(FuzzyMatch.matches("buddha", "Buddhafield"))
        XCTAssertTrue(FuzzyMatch.matches("Subhuti", "subhuti"))
    }

    func testToleratesTyposScaledByLength() {
        XCTAssertTrue(FuzzyMatch.matches("adhistana", "Adhisthana"))
        XCTAssertTrue(FuzzyMatch.matches("sanghrakshita", "Sangharakshita"))
        XCTAssertTrue(FuzzyMatch.matches("padmaloka", "Padmaloka Retreat Centre"))
        XCTAssertTrue(FuzzyMatch.matches("vesantara", "Vessantara"))
        XCTAssertFalse(FuzzyMatch.matches("oxf", "Oakford"))
    }

    func testShortQueriesAreExactOnly() {
        XCTAssertEqual(FuzzyMatch.allowedEdits(4), 0)
        XCTAssertEqual(FuzzyMatch.allowedEdits(5), 1)
        XCTAssertEqual(FuzzyMatch.allowedEdits(8), 2)
        XCTAssertFalse(FuzzyMatch.matches("bxdh", "Bodh Gaya"))
    }

    func testRejectsUnrelatedNames() {
        XCTAssertFalse(FuzzyMatch.matches("adhisthana", "Aryaloka Buddhist Center"))
        XCTAssertFalse(FuzzyMatch.matches("valencia", "Vajrasana Retreat Centre"))
        XCTAssertFalse(FuzzyMatch.matches("manchester", "Cambridge"))
    }

    func testIgnoresDiacriticsAndCase() {
        XCTAssertTrue(FuzzyMatch.matches("sao paulo", "São Paulo"))
        XCTAssertTrue(FuzzyMatch.matches("centro budista satelite", "Centro Budista Satélite"))
        XCTAssertEqual(FuzzyMatch.distance("adhistana", "adhisthana"), 1)
        XCTAssertEqual(FuzzyMatch.distance("teh", "the"), 1)
    }
}
