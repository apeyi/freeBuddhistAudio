import SwiftUI

/// Generated tile artwork (SPEC "Tile artwork", tokens.json `artwork`): colour sets and placement,
/// pure so it's unit-tested; `TileArtwork` draws it. The symbols are Figma placeholders until
/// Dharmachakra's final set arrives. Mirrors Android `TileArt`.
enum TileArt {
    struct Colors: Equatable { let main: UInt32; let partner: UInt32; let glow: UInt32 }
    struct Wash: Equatable { let x: Double; let y: Double }
    struct HomeTile { let title: String; let colors: Colors; let symbol: TileSymbol; let wash: Wash; var orderOnly = false }

    static let washes = [Wash(x: -0.035, y: -0.035), Wash(x: 0.262, y: -0.214), Wash(x: -0.163, y: 0.259)]

    static let homeTiles: [HomeTile] = [
        HomeTile(title: "Introducing Buddhism", colors: Colors(main: 0x979DFB, partner: 0xFF42D3, glow: 0x00ADEF), symbol: .wheel, wash: washes[0]),
        HomeTile(title: "Meditation & mindfulness", colors: Colors(main: 0x5ABA83, partner: 0x00ADEF, glow: 0x979DFB), symbol: .lotus, wash: washes[1]),
        HomeTile(title: "Latest talks & meditations", colors: Colors(main: 0xEC5E2B, partner: 0xD13D7F, glow: 0xEA1221), symbol: .vihara, wash: washes[2]),
        HomeTile(title: "Themes", colors: Colors(main: 0x00ADEF, partner: 0x8B3DF6, glow: 0xFF42D3), symbol: .wheel, wash: washes[1]),
        HomeTile(title: "Series", colors: Colors(main: 0x659CAE, partner: 0x979DFB, glow: 0x5ABA83), symbol: .lotus, wash: washes[0]),
        HomeTile(title: "People", colors: Colors(main: 0xEA1221, partner: 0xFF42D3, glow: 0xEC5E2B), symbol: .buddha, wash: washes[1]),
        HomeTile(title: "Places", colors: Colors(main: 0x3E8C27, partner: 0x659CAE, glow: 0x5ABA83), symbol: .wheel, wash: washes[2]),
        HomeTile(title: "Order talks", colors: Colors(main: 0x006AA3, partner: 0x00ADEF, glow: 0x979DFB), symbol: .lotus, wash: washes[0], orderOnly: true),
    ]

    static let rainbow: [Colors] = [
        Colors(main: 0xEA1221, partner: 0xFF42D3, glow: 0xFFC93C), Colors(main: 0xEC5E2B, partner: 0xFFC93C, glow: 0xFF42D3),
        Colors(main: 0x5ABA83, partner: 0xFFC93C, glow: 0xEC5E2B), Colors(main: 0x5ABA83, partner: 0x00ADEF, glow: 0xFFC93C),
        Colors(main: 0x659CAE, partner: 0x5ABA83, glow: 0xFFC93C), Colors(main: 0x00ADEF, partner: 0x979DFB, glow: 0x5ABA83),
        Colors(main: 0x979DFB, partner: 0xFF42D3, glow: 0x00ADEF), Colors(main: 0x8B3DF6, partner: 0xFF42D3, glow: 0x979DFB),
        Colors(main: 0xD13D7F, partner: 0x8B3DF6, glow: 0xFF42D3), Colors(main: 0xFF42D3, partner: 0xEC5E2B, glow: 0xFFC93C),
    ]

    static let symbolSafe: [Colors] = [
        Colors(main: 0xEA1221, partner: 0xFF42D3, glow: 0xEC5E2B), Colors(main: 0xEC5E2B, partner: 0xFF42D3, glow: 0xEA1221),
        Colors(main: 0x5ABA83, partner: 0x00ADEF, glow: 0x979DFB), Colors(main: 0x659CAE, partner: 0x5ABA83, glow: 0x00ADEF),
        Colors(main: 0xFF42D3, partner: 0xEC5E2B, glow: 0x979DFB),
    ]

    static func rainbowAt(_ index: Int, start: Int) -> Colors { rainbow[((start + index) % rainbow.count + rainbow.count) % rainbow.count] }
    static func symbolSafeAt(_ index: Int, start: Int) -> Colors { symbolSafe[((start + index) % symbolSafe.count + symbolSafe.count) % symbolSafe.count] }

    static let yellow: UInt32 = 0xFFC93C
    static let symbolAlpha = 0.22
    static let symbolHeight = 0.72
    static let symbolCentre = UnitPoint(x: 0.76, y: 0.44)
}

/// Draws a generated tile: main colour into its partner along the diagonal, a broad glow of the
/// third colour bottom right, the main colour again bottom left, a soft white wash clear of the
/// symbol and title, then one white symbol at 22% and the title. Mirrors Android `TileArtwork`.
struct TileArtwork: View {
    let colors: TileArt.Colors
    var symbol: TileSymbol? = nil
    var wash: TileArt.Wash = TileArt.washes[0]
    var title: String? = nil
    var caption: String? = nil

    var body: some View {
        let main = Color(hex: colors.main), partner = Color(hex: colors.partner), glow = Color(hex: colors.glow)
        GeometryReader { geo in
            ZStack(alignment: .bottomLeading) {
                LinearGradient(stops: [.init(color: main, location: 0.143), .init(color: partner, location: 0.571),
                                       .init(color: partner, location: 0.857)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
                EllipticalGradient(stops: [.init(color: glow, location: 0), .init(color: glow.opacity(0.85), location: 0.35),
                                           .init(color: glow.opacity(0.35), location: 0.7), .init(color: glow.opacity(0), location: 1)],
                                   center: UnitPoint(x: 1, y: 0.95), startRadiusFraction: 0, endRadiusFraction: 0.70)
                EllipticalGradient(stops: [.init(color: main.opacity(0.7), location: 0), .init(color: main.opacity(0.35), location: 0.5),
                                           .init(color: main.opacity(0), location: 1)],
                                   center: UnitPoint(x: 0, y: 1), startRadiusFraction: 0, endRadiusFraction: 0.45)
                EllipticalGradient(stops: [.init(color: .white.opacity(0.85), location: 0), .init(color: .white.opacity(0.765), location: 0.3),
                                           .init(color: .white.opacity(0.3825), location: 0.6), .init(color: .white.opacity(0), location: 1)],
                                   center: UnitPoint(x: wash.x, y: wash.y), startRadiusFraction: 0, endRadiusFraction: 0.675)
                if let symbol {
                    let h = geo.size.height * TileArt.symbolHeight
                    symbol.shape.fill(.white).opacity(TileArt.symbolAlpha)
                        .frame(width: h * symbol.viewBox.width / symbol.viewBox.height, height: h)
                        .position(x: geo.size.width * TileArt.symbolCentre.x, y: geo.size.height * TileArt.symbolCentre.y)
                }
                if let caption {
                    Text(caption).font(.caption2).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(14)
                }
                if let title {
                    Text(title).font(.callout.weight(.medium)).foregroundStyle(.white)
                        .multilineTextAlignment(.leading)
                        .padding(.horizontal, 14).padding(.bottom, 12)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// Where a featured post opens (pure, tested): talks and series natively, the Digital Legacy page
/// natively, anything else on the website. Mirrors Android `FeaturedLink`.
enum FeaturedTarget: Equatable { case talk(String), series(String), digitalLegacy, web(String) }

enum FeaturedLink {
    static func target(_ url: String) -> FeaturedTarget {
        let num = ContentSource.queryValue("num", in: url)
        if url.contains("/audio/details"), let num { return .talk(num) }
        if url.contains("/series/details"), let num { return .series(num) }
        if url.range(of: #"/digital-legacy/?($|[?#])"#, options: .regularExpression) != nil { return .digitalLegacy }
        return .web(url)
    }

    static let bands: [UInt32] = [0x3A8425, 0xA85D21, 0x006AA3, 0xD0397C, 0x4A7C8C, 0x8B3DF6, 0x368257, 0x5761F9]
    static func band(_ index: Int) -> UInt32 { bands[((index % bands.count) + bands.count) % bands.count] }
}
