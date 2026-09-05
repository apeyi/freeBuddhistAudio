import Foundation

/// Auto-generated artwork for collections without a cover image: a gradient
/// whose hues derive from the slug, so the same collection always looks the
/// same. Mirrors Android's CollectionArtwork (same FNV-1a hash → same hues).
enum CollectionArtwork {

    /// Stable hue in [0, 360) for a slug. Empty slug → 0.
    static func hue(_ slug: String) -> Double {
        if slug.trimmingCharacters(in: .whitespaces).isEmpty { return 0 }
        var h: UInt32 = 0x811C9DC5
        for scalar in slug.lowercased().unicodeScalars {
            // Android hashes UTF-16 code units (Kotlin Char); stay in the BMP-compatible path.
            for unit in String(scalar).utf16 {
                h ^= UInt32(unit)
                h = h &* 0x01000193
            }
        }
        return Double(h % 360)
    }

    /// Second gradient stop: 40° around the wheel so the gradient reads as one colour family.
    static func secondHue(_ slug: String) -> Double {
        (hue(slug) + 40).truncatingRemainder(dividingBy: 360)
    }
}
