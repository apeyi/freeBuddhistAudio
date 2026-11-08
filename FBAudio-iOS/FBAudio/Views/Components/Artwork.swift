import SwiftUI
import UIKit

/// The design's talk artwork rule (SPEC "Talk artwork: which image to show"), pure so it can be
/// unit-tested. Always shown as a rounded rectangle, never a circle. Mirrors Android `ArtworkRule`.
enum ArtworkRule {
    /// Which image to show for a talk: the banner, else the speaker image, else the talk's own
    /// list image; nil means the "Various" Standing Buddha fallback. Blank URLs and the site's
    /// generic "/default" placeholders count as missing.
    static func talkImage(bannerUrl: String?, speakerImageUrl: String?, listImageUrl: String?) -> String? {
        [bannerUrl, speakerImageUrl, listImageUrl].first { usable($0) } ?? nil
    }

    static func usable(_ url: String?) -> Bool {
        guard let url, !url.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        return !url.contains("/default")
    }

    /// A transparent or near-white pixel: the background around a pre-cut circle.
    static func isBlank(r: Int, g: Int, b: Int, a: Int) -> Bool { a <= 16 || (r >= 245 && g >= 245 && b >= 245) }

    /// Whether an image is a pre-cut circle: blank (transparent or white, e.g. Kalyacitta's JPEG) at all
    /// four corners and just inside them, with a non-blank centre. `blank(x, y)` reads one pixel.
    static func looksPreCutCircle(width: Int, height: Int, blank: (Int, Int) -> Bool) -> Bool {
        guard width >= 16, height >= 16 else { return false }
        let ins = max(Int(Double(min(width, height)) * 0.07), 2)
        let points = [(2, 2), (width - 3, 2), (2, height - 3), (width - 3, height - 3),
                      (ins, ins), (width - 1 - ins, ins), (ins, height - 1 - ins), (width - 1 - ins, height - 1 - ins)]
        return points.allSatisfy { blank($0.0, $0.1) } && !blank(width / 2, height / 2)
    }

    struct Crop: Equatable {
        let x: Int, y: Int, width: Int, height: Int
    }

    /// Some speaker images are stored pre-cut into a circle (transparent corners). Crop the
    /// largest rectangle of the target `aspect` (width / height) that fits inside that circle,
    /// so no corner or circle edge shows and the face keeps its proportions.
    static func inscribedCrop(width: Int, height: Int, aspect: Double) -> Crop {
        let diameter = Double(min(width, height))
        let h = diameter / (1 + aspect * aspect).squareRoot()
        let w = h * aspect
        // Floor, not round: rounding up can put the crop corners just outside the circle.
        let cw = min(max(Int(w), 1), width)
        let ch = min(max(Int(h), 1), height)
        return Crop(x: (width - cw) / 2, y: (height - ch) / 2, width: cw, height: ch)
    }
}

/// Loads and caches artwork, cropping pre-cut circular images to a rectangle inside the circle.
actor ArtworkLoader {
    static let shared = ArtworkLoader()
    private let cache = NSCache<NSString, UIImage>()

    func image(for url: String, aspect: Double) async -> UIImage? {
        let key = "\(url)|\(aspect)" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        guard let u = URL(string: url),
              let (data, _) = try? await URLSession.shared.data(from: u),
              let raw = UIImage(data: data) else { return nil }
        let out = Self.circleSafe(raw, aspect: aspect)
        cache.setObject(out, forKey: key)
        return out
    }

    private static func circleSafe(_ image: UIImage, aspect: Double) -> UIImage {
        guard let cg = image.cgImage,
              ArtworkRule.looksPreCutCircle(width: cg.width, height: cg.height, blank: { x, y in isBlank(cg, x: x, y: y) })
        else { return image }
        let c = ArtworkRule.inscribedCrop(width: cg.width, height: cg.height, aspect: aspect)
        guard let cropped = cg.cropping(to: CGRect(x: c.x, y: c.y, width: c.width, height: c.height)) else { return image }
        return UIImage(cgImage: cropped, scale: image.scale, orientation: image.imageOrientation)
    }

    /// Whether one pixel is blank (transparent or near-white), by drawing it into a 1×1 RGBA context.
    private static func isBlank(_ cg: CGImage, x: Int, y: Int) -> Bool {
        var pixel = [UInt8](repeating: 0, count: 4)
        guard let ctx = CGContext(data: &pixel, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
        ctx.draw(cg, in: CGRect(x: -x, y: -(cg.height - 1 - y), width: cg.width, height: cg.height))
        let a = Int(pixel[3])
        guard a > 16 else { return true }
        // Un-premultiply before checking for white.
        func ch(_ v: UInt8) -> Int { min(255, Int(v) * 255 / a) }
        return ArtworkRule.isBlank(r: ch(pixel[0]), g: ch(pixel[1]), b: ch(pixel[2]), a: a)
    }
}

/// An artwork image following the design rule: `url` if usable, else the "Various" Standing
/// Buddha. Callers clip to the rounded shape (radius 16 artwork, 8 thumbnails), never a circle.
struct Artwork: View {
    let url: String?
    var aspect: Double = 1
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        // Color.clear takes exactly the size it's offered; the image is drawn as an overlay.
        // A scaledToFill image as the layout child would report its natural size instead,
        // so a wide 4:1 banner at 240pt high claimed ~960pt and pushed the page off-screen.
        Color.clear
            .overlay {
                if let image {
                    Image(uiImage: image).resizable().scaledToFill()
                } else if !ArtworkRule.usable(url) || failed {
                    BundleImage(name: "various").scaledToFill()
                } else {
                    FbaColors.circle
                }
            }
            .clipped()
            .task(id: url) {
            image = nil
            failed = false
            guard let url, ArtworkRule.usable(url) else { return }
            if let loaded = await ArtworkLoader.shared.image(for: url, aspect: aspect) {
                image = loaded
            } else {
                failed = true
            }
        }
        .accessibilityHidden(true)
    }
}

/// Talk artwork: banner, else speaker image, else list image, else "Various".
struct TalkArtwork: View {
    let talk: Talk
    var aspect: Double = 1

    var body: some View {
        Artwork(url: ArtworkRule.talkImage(bannerUrl: talk.bannerUrl, speakerImageUrl: talk.speakerImageUrl,
                                           listImageUrl: talk.imageUrl),
                aspect: aspect)
    }
}
