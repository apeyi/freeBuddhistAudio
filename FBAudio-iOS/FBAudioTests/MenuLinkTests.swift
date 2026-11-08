import XCTest
@testable import FBAudio

/// v2 menu kinds (draft.5) → the domain link shapes the navigation routes on (parity with Android MenuLinkTest).
final class MenuLinkTests: XCTestCase {

    private func node(_ kind: V2MenuNodeKind, _ target: String?, webUrl: String? = nil) -> MenuNode {
        MenuNode(label: "x", link: V2Catalogue.menuLink(kind, target: target, webUrl: webUrl))
    }

    func testTalkKindOpensTheTalk() {
        let n = node(.talk, "LOC3883")
        XCTAssertEqual(n.talkCatNum, "LOC3883")
        XCTAssertNil(n.toSource())
        XCTAssertFalse(n.isExternal)
    }

    func testIndexKindOpensTheFullIndex() {
        for target in ["speakers", "places", "years", "themes"] {
            let n = node(.index, target)
            XCTAssertEqual(n.toSource(), .apiCollection(target, title: "x"))
            XCTAssertNil(n.talkCatNum)
        }
    }

    func testExistingKindsUnchanged() {
        XCTAssertEqual(node(.collection, "ethics").toSource(), .namedCollection("ethics"))
        XCTAssertEqual(node(.series, "X06").toSource(), .series("/series/details?num=X06"))
        XCTAssertEqual(node(.place, "Adhisthana").toSource(), .browse("/browse?p=Adhisthana"))
        XCTAssertEqual(V2Catalogue.menuLink(.group, target: nil, webUrl: nil), "#")
        XCTAssertEqual(V2Catalogue.menuLink(.page, target: nil, webUrl: "https://example.org/about"), "https://example.org/about")
        XCTAssertEqual(V2Catalogue.menuLink(.unknown, target: "t", webUrl: nil), "#")
    }

    func testTalkCatNumOnlyForTalkLinks() {
        XCTAssertNil(MenuNode(label: "a", link: "/series/details?num=X06").talkCatNum)
        XCTAssertEqual(MenuNode(label: "a", link: "https://www.freebuddhistaudio.com/audio/details?num=LOC7132#x").talkCatNum, "LOC7132")
    }
}
