import Foundation

/// Colour tokens from the v1.0 design (docs/design/tokens.json, Figma "FBA Palette").
/// Plain 0xRRGGBB values with no SwiftUI dependency so the cycle logic is unit-testable;
/// Theme.swift turns them into Colors. Never hard-code a colour that has a token here.
enum FbaPalette {
    // Neutral
    static let black: UInt32 = 0x000000
    static let grey900: UInt32 = 0x262626   // "FBA black": speaker names, near-black links
    static let grey700: UInt32 = 0x575757
    static let grey600: UInt32 = 0x757575   // meta text (length, year, place)
    static let grey500: UInt32 = 0x949494
    static let grey200: UInt32 = 0xE5E5E5   // dividers
    static let grey100: UInt32 = 0xF2F2F2   // tool circles, play circles, Now Playing bar
    static let grey50: UInt32 = 0xF8F8F8
    static let white: UInt32 = 0xFFFFFF

    // UX extras
    static let sangharakshitaOrange: UInt32 = 0xD53424
    static let heartRed: UInt32 = 0xFF4031
    static let contrastRed: UInt32 = 0xDB0000   // destructive actions (Sign out, Remove)

    /// Proposed addition for artwork gradients only: a partner or glow, never under white text.
    static let proposedYellow: UInt32 = 0xFFC93C
}

/// One colour family: `base` for artwork, `deep` for buttons/links/readable white text, `tint` for pale fills.
struct AccentSet: Equatable, Hashable {
    let base: UInt32
    let deep: UInt32
    let tint: UInt32
}

/// The site's brand colours, each with base/deep/tint shades.
enum Brand: CaseIterable {
    case fbaBlue, fbaGreen, orderOnlyBlue, fbaOrange, fbaChestnut, fbaFuschia, fbaLightGreen,
         fbaLilac, fbaLightMagenta, fbaPurple, fbaNewRed, fbaSierra

    var set: AccentSet {
        switch self {
        case .fbaBlue: return AccentSet(base: 0x00ADEF, deep: 0x007AA8, tint: 0xE0F5FD)
        case .fbaGreen: return AccentSet(base: 0x3E8C27, deep: 0x3A8425, tint: 0xE8F1E5)
        case .orderOnlyBlue: return AccentSet(base: 0x006AA3, deep: 0x006AA3, tint: 0xE0EDF4)
        case .fbaOrange: return AccentSet(base: 0xEC5E2B, deep: 0xCD4412, tint: 0xFDECE6)
        case .fbaChestnut: return AccentSet(base: 0xA85D21, deep: 0xA85D21, tint: 0xF5ECE4)
        case .fbaFuschia: return AccentSet(base: 0xD13D7F, deep: 0xD0397C, tint: 0xF9E8F0)
        case .fbaLightGreen: return AccentSet(base: 0x5ABA83, deep: 0x368257, tint: 0xEBF7F0)
        case .fbaLilac: return AccentSet(base: 0x979DFB, deep: 0x5761F9, tint: 0xF3F3FF)
        case .fbaLightMagenta: return AccentSet(base: 0xFF42D3, deep: 0xD600A4, tint: 0xFFE8FA)
        case .fbaPurple: return AccentSet(base: 0x8B3DF6, deep: 0x8B3DF6, tint: 0xF1E8FE)
        case .fbaNewRed: return AccentSet(base: 0xEA1221, deep: 0xE51220, tint: 0xFCE3E4)
        case .fbaSierra: return AccentSet(base: 0x659CAE, deep: 0x4A7C8C, tint: 0xEDF3F5)
        }
    }
}

/// Digital Legacy gold: replaces the accent everywhere on a remastered talk or series.
let remasterGold = AccentSet(base: 0xDBAF55, deep: 0x8A6A1F, tint: 0xF8EFDD)

/// Order-only features and the "Order member" badge always use this, never the hourly accent.
let orderOnlyAccent = Brand.orderOnlyBlue.set

/// The hourly accent, as on freebuddhistaudio.com: one colour mode per hour, default FBA Blue.
///
/// The website picks its colour server-side and doesn't publish the rule (asked FBA to expose it
/// in /meta), so for now this steps through the design's mode order by hour of day, UK time.
/// Everything goes through `mode(for:)`, so matching the site later is a one-line change.
/// Mirrors Android `AccentCycle`.
enum AccentCycle {
    /// Mode order from tokens.json `accent.modes`.
    static let modes: [Brand] = [.fbaBlue, .fbaGreen, .orderOnlyBlue, .fbaOrange, .fbaChestnut,
                                 .fbaFuschia, .fbaLightGreen, .fbaLilac, .fbaNewRed, .fbaPurple]
    static let defaultMode: Brand = .fbaBlue
    static let siteTimeZone = TimeZone(identifier: "Europe/London")!

    static func mode(for date: Date, timeZone: TimeZone = siteTimeZone) -> Brand {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let hour = cal.component(.hour, from: date)
        return modes[hour % modes.count]
    }

    /// Seconds until the next whole hour, when the accent may change.
    static func secondsToNextHour(_ date: Date) -> TimeInterval {
        let t = date.timeIntervalSince1970
        return 3600 - t.truncatingRemainder(dividingBy: 3600)
    }
}

/// Which colour family a page uses.
enum PageTheme { case hourly, remastered, orderOnly }

/// The colour family for `theme` given the current hourly accent.
func accentFor(_ theme: PageTheme, hourly: AccentSet) -> AccentSet {
    switch theme {
    case .hourly: return hourly
    case .remastered: return remasterGold
    case .orderOnly: return orderOnlyAccent
    }
}
