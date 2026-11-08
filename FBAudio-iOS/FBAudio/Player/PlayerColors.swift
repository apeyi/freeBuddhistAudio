import Foundation

/// Full-screen player colour (SPEC "Player colour", 7 Oct): a top-to-bottom gradient between two
/// colours found in the artwork, the strongest at the top and a deeper one of a different hue at
/// the bottom, lightly softened, with white text. Black-and-white artwork falls back to the
/// hourly colour; remastered talks are always gold. Pure 0xRRGGBB maths, unit-tested; mirrors
/// Android `PlayerColors`.
enum PlayerColors {
    struct Gradient: Equatable { let top: UInt32; let mid: UInt32; let bottom: UInt32 }

    static let greyscaleSaturation = 0.2
    static let maxTopLuminance = 0.30
    static let maxBottomLuminance = 0.12

    /// nil means "no usable colour" (black and white or empty artwork): use `forAccent`.
    static func fromColors(_ colors: [UInt32]) -> Gradient? {
        let saturated = colors.filter { saturation($0) >= greyscaleSaturation }
        guard let top = saturated.max(by: { score($0) < score($1) }) else { return nil }
        let bottom = saturated
            .filter { $0 != top && hueDistance($0, top) >= 40 && luminance($0) <= luminance(top) + 0.05 }
            .max(by: { saturation($0) - luminance($0) < saturation($1) - luminance($1) })
            ?? mix(top, 0x000000, 0.5)
        return finish(top: top, bottom: bottom)
    }

    static func forAccent(_ set: AccentSet) -> Gradient {
        finish(top: set.base, bottom: mix(set.deep, 0x000000, 0.35))
    }

    static func forGold() -> Gradient { finish(top: remasterGold.base, bottom: remasterGold.deep) }

    /// Darken each end until white text is readable; the midpoint sits 45% down (Figma).
    static func finish(top: UInt32, bottom: UInt32) -> Gradient {
        let t = darken(top, to: maxTopLuminance)
        let b = darken(bottom, to: maxBottomLuminance)
        return Gradient(top: t, mid: mix(t, b, 0.45), bottom: b)
    }

    private static func score(_ c: UInt32) -> Double { saturation(c) - max(luminance(c) - 0.25, 0) * 2 }

    // MARK: colour maths

    private static func r(_ c: UInt32) -> Double { Double((c >> 16) & 0xFF) }
    private static func g(_ c: UInt32) -> Double { Double((c >> 8) & 0xFF) }
    private static func b(_ c: UInt32) -> Double { Double(c & 0xFF) }

    static func saturation(_ c: UInt32) -> Double {
        let mx = max(r(c), g(c), b(c)), mn = min(r(c), g(c), b(c))
        return mx == 0 ? 0 : (mx - mn) / mx
    }

    static func luminance(_ c: UInt32) -> Double {
        func ch(_ v: Double) -> Double { let s = v / 255; return s <= 0.03928 ? s / 12.92 : pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(r(c)) + 0.7152 * ch(g(c)) + 0.0722 * ch(b(c))
    }

    static func hue(_ c: UInt32) -> Double {
        let rf = r(c) / 255, gf = g(c) / 255, bf = b(c) / 255
        let mx = max(rf, gf, bf), mn = min(rf, gf, bf), d = mx - mn
        guard d > 0 else { return 0 }
        var h: Double
        if mx == rf { h = ((gf - bf) / d).truncatingRemainder(dividingBy: 6) }
        else if mx == gf { h = (bf - rf) / d + 2 }
        else { h = (rf - gf) / d + 4 }
        h *= 60
        return h < 0 ? h + 360 : h
    }

    static func hueDistance(_ a: UInt32, _ b: UInt32) -> Double {
        let d = abs(hue(a) - hue(b))
        return min(d, 360 - d)
    }

    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
        func m(_ x: Double, _ y: Double) -> UInt32 { UInt32(min(max(x + (y - x) * t, 0), 255)) }
        return (m(r(a), r(b)) << 16) | (m(g(a), g(b)) << 8) | m(self.b(a), self.b(b))
    }

    static func darken(_ c: UInt32, to maxLuminance: Double) -> UInt32 {
        var out = c
        var step = 0
        while luminance(out) > maxLuminance && step < 40 { out = mix(out, 0x000000, 0.06); step += 1 }
        return out
    }

    static func contrastWithWhite(_ c: UInt32) -> Double { 1.05 / (luminance(c) + 0.05) }
}

/// The artwork's main colours, from a small grid of its pixels (iOS has no Palette): saturated
/// pixels are grouped by hue (15° bins) and lightness, averaged, and returned most common
/// first. Greys are left out, so black-and-white art gives an empty list.
enum ArtworkPalette {
    static func colors(pixels: [UInt32], minShare: Double = 0.02) -> [UInt32] {
        struct Bucket { var r = 0.0, g = 0.0, b = 0.0, n = 0 }
        var buckets: [Int: Bucket] = [:]
        for p in pixels where PlayerColors.saturation(p) >= PlayerColors.greyscaleSaturation {
            let hueBin = Int(PlayerColors.hue(p) / 15) % 24
            let lum = PlayerColors.luminance(p)
            let lightBin = lum < 0.08 ? 0 : (lum < 0.3 ? 1 : 2)
            let key = hueBin * 3 + lightBin
            var bucket = buckets[key] ?? Bucket()
            bucket.r += Double((p >> 16) & 0xFF); bucket.g += Double((p >> 8) & 0xFF); bucket.b += Double(p & 0xFF)
            bucket.n += 1
            buckets[key] = bucket
        }
        let minCount = max(1, Int(Double(pixels.count) * minShare))
        return buckets.values
            .filter { $0.n >= minCount }
            .sorted { $0.n > $1.n }
            .map { b in
                let n = Double(b.n)
                return (UInt32(b.r / n) << 16) | (UInt32(b.g / n) << 8) | UInt32(b.b / n)
            }
    }
}
