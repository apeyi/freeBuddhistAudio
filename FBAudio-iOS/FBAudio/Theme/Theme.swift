import SwiftUI

extension Color {
    /// A colour from a 0xRRGGBB token.
    init(hex: UInt32) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: 1)
    }
}

/// The current page's colour family (hourly accent, gold or Order blue). Read it with
/// `@Environment(\.pageColors)`; controls also pick up `deep` through `.tint`.
struct PageColors: Equatable {
    let base: Color
    let deep: Color
    let tint: Color

    init(_ set: AccentSet) {
        base = Color(hex: set.base)
        deep = Color(hex: set.deep)
        tint = Color(hex: set.tint)
    }
}

/// Neutral greys from the palette, for text and surfaces that never follow the accent.
enum FbaColors {
    static let black = Color(hex: FbaPalette.black)
    static let nearBlack = Color(hex: FbaPalette.grey900)
    static let grey700 = Color(hex: FbaPalette.grey700)
    static let meta = Color(hex: FbaPalette.grey600)
    static let grey500 = Color(hex: FbaPalette.grey500)
    static let divider = Color(hex: FbaPalette.grey200)
    static let circle = Color(hex: FbaPalette.grey100)
    static let grey50 = Color(hex: FbaPalette.grey50)
    static let white = Color(hex: FbaPalette.white)
    static let destructive = Color(hex: FbaPalette.contrastRed)
    static let orderOnly = PageColors(orderOnlyAccent)
    static let gold = PageColors(remasterGold)
}

/// Layout and shape tokens (tokens.json `layout`, iOS column).
enum FbaDimens {
    static let margin: CGFloat = 20
    static let radiusArtwork: CGFloat = 16
    static let radiusTile: CGFloat = 16
    static let radiusThumb: CGFloat = 8
    static let radiusButton: CGFloat = 12
    static let buttonHeight: CGFloat = 48
    static let playCircle: CGFloat = 36
    static let toolCircle: CGFloat = 52
    static let minTouch: CGFloat = 44
}

/// Type scale (tokens.json `typography.ios`), on Dynamic Type text styles so it scales:
/// page titles 28 Bold, section headings 20 Bold, body 15/16, meta 13 grey.
extension Font {
    static let fbaPageTitle = Font.title.weight(.bold)          // 28
    static let fbaSectionTitle = Font.title3.weight(.bold)      // 20
    static let fbaBody = Font.callout                           // 16
    static let fbaBodySmall = Font.subheadline                  // 15
    static let fbaMeta = Font.footnote                          // 13
}

private struct PageColorsKey: EnvironmentKey {
    static let defaultValue = PageColors(AccentCycle.defaultMode.set)
}
private struct HourlyAccentKey: EnvironmentKey {
    static let defaultValue = AccentCycle.defaultMode.set
}

extension EnvironmentValues {
    var pageColors: PageColors {
        get { self[PageColorsKey.self] }
        set { self[PageColorsKey.self] = newValue }
    }
    /// The app-wide hourly accent, so a re-themed page can still reach it.
    var hourlyAccent: AccentSet {
        get { self[HourlyAccentKey.self] }
        set { self[HourlyAccentKey.self] = newValue }
    }
}

/// Publishes the hourly accent and re-evaluates it at the top of each hour.
@MainActor
final class AccentClock: ObservableObject {
    @Published private(set) var accent: AccentSet = AccentClock.current()
    private var timer: Timer?

    init() { schedule() }

    /// The hourly accent, unless pinned with the `-fbaAccent <brand>` launch argument
    /// (CI screenshots compare against a Figma frame shown in one colour).
    nonisolated static func current() -> AccentSet {
        if let pinned = UserDefaults.standard.string(forKey: "fbaAccent"),
           let brand = Brand.allCases.first(where: { "\($0)" == pinned }) {
            return brand.set
        }
        return AccentCycle.mode(for: Date()).set
    }

    private func schedule() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: AccentCycle.secondsToNextHour(Date()) + 0.5,
                                     repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.accent = AccentClock.current()
                self.schedule()
            }
        }
    }
}

private struct PageThemeModifier: ViewModifier {
    let theme: PageTheme
    @Environment(\.hourlyAccent) private var hourly

    func body(content: Content) -> some View {
        let colors = PageColors(accentFor(theme, hourly: hourly))
        content
            .environment(\.pageColors, colors)
            .tint(colors.deep)
    }
}

extension View {
    /// App root: installs the hourly accent (deep shade as the control tint).
    func fbaHourlyTheme(_ accent: AccentSet) -> some View {
        environment(\.hourlyAccent, accent)
            .environment(\.pageColors, PageColors(accent))
            .tint(PageColors(accent).deep)
    }

    /// Re-themes a page: remastered talks/series go gold everywhere on the page,
    /// Order-only features go Order blue. Mirrors Android `PageColorScope`.
    func fbaPageTheme(_ theme: PageTheme) -> some View {
        modifier(PageThemeModifier(theme: theme))
    }
}
