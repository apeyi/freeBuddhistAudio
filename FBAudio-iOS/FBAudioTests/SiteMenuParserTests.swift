import XCTest
@testable import FBAudio

final class SiteMenuParserTests: XCTestCase {

    // Trimmed sample of document.__FBA__.sidebar_menu as served on 2026-09-05
    private let sample = """
    {"items":[
      {"classes":["mobile-menu"],"label":"collections","link":"#","om":false,"children":[
        {"label":"latest talks & meditations","link":"/browse?cat=latest&t=audio ","om":false,"children":[]},
        {"label":"the buddha","link":"/collection/buddha","om":false,"children":[]},
        {"label":"meditation & mindfulness","link":"#meditation-and-mindfulness","om":false,"children":[
          {"label":"all meditation & mindfulness","link":"/collection/meditation-mindfulness","om":false,"children":[]},
          {"label":"body awareness","link":"/collection/body-awareness ","om":false,"children":[]}
        ]},
        {"label":"all series","link":"/series/","om":false,"children":[]},
        {"label":"all speakers","link":"/browse?cat=speakers&t=audio","om":false,"children":[]}
      ]},
      {"label":"themes","link":"#","om":false,"children":[
        {"label":"all themes","link":"/browse?cat=themes&t=audio","om":false,"children":[]},
        {"label":"bodhisattva ideal","link":"/collection/bodhisattva-ideal","om":false,"children":[]}
      ]},
      {"label":"people","link":"#","om":false,"children":[
        {"label":"amalamati (en espa&ntilde;ol)","link":"/browse?s=Amalamati","om":false,"children":[]},
        {"label":"subhuti","link":"/collection/subhuti","om":true,"children":[]}
      ]}
    ]}
    """

    func testParsesTopLevelSectionsAndChildren() {
        let menu = SiteMenuParser.parse(jsonString: sample)
        XCTAssertEqual(menu.map(\.label), ["collections", "themes", "people"])
        XCTAssertEqual(menu[0].children.count, 5)
        XCTAssertEqual(menu[0].children[2].children.count, 2)
    }

    func testUnescapesEntitiesAndTrimsLinks() {
        let menu = SiteMenuParser.parse(jsonString: sample)
        XCTAssertEqual(SiteMenuParser.section(menu, "people")?.children[0].label, "amalamati (en español)")
        XCTAssertEqual(menu[0].children[0].link, "/browse?cat=latest&t=audio")
    }

    func testReadsOmFlag() {
        let people = SiteMenuParser.section(SiteMenuParser.parse(jsonString: sample), "people")!
        XCTAssertFalse(people.children[0].om)
        XCTAssertTrue(people.children[1].om)
    }

    func testCollectionTilesFlattenOneLevelAndSkipIndexPages() {
        let tiles = SiteMenuParser.collectionTiles(SiteMenuParser.parse(jsonString: sample))
        XCTAssertEqual(tiles.map(\.collectionSlug), ["buddha", "meditation-mindfulness", "body-awareness"])
    }

    func testMenuNodesResolveToContentSources() {
        let menu = SiteMenuParser.parse(jsonString: sample)
        let collections = menu[0].children
        XCTAssertEqual(collections[0].toSource(), .apiCollection("latest", title: "latest talks & meditations"))
        XCTAssertEqual(collections[1].toSource(), .namedCollection("buddha"))
        XCTAssertNil(collections[2].toSource()) // "#" placeholder with children
        XCTAssertEqual(collections[4].toSource(), .apiCollection("speakers", title: "all speakers"))
        XCTAssertEqual(menu[2].children[0].toSource(), .browse("/browse?s=Amalamati"))
    }

    func testContentSourceRoundTripsThroughEncoding() {
        let sources: [ContentSource] = [
            .apiCollection("all_series", title: "Series"),
            .namedCollection("bodhisattva-ideal"),
            .browse("/browse?s=Vajragupta_(m)&t=audio"),
            .series("/series/details?num=X16"),
        ]
        for s in sources { XCTAssertEqual(ContentSource.decode(s.encode()), s) }
        XCTAssertNil(ContentSource.decode("garbage"))
    }
}
