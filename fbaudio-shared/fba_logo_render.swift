// Regenerates the FBA logo assets in fbaudio-shared/images/.
// Run from the repo root:  swift fbaudio-shared/fba_logo_render.swift
// Colours sampled from the original fba-half-size.jpg; font: Lucida Grande Bold
// (matched against the original — all three letters line up).
import AppKit
import ImageIO
import UniformTypeIdentifiers

let outDir = "fbaudio-shared/images"
let cF = NSColor(deviceRed: 0xFE/255, green: 0xD0/255, blue: 0x23/255, alpha: 1) // f gold
let cB = NSColor(deviceRed: 0x0D/255, green: 0xBF/255, blue: 0xF3/255, alpha: 1) // b blue
let cA = NSColor(deviceRed: 0xFE/255, green: 0x36/255, blue: 0x17/255, alpha: 1) // a red

func attr(_ size: CGFloat) -> NSAttributedString {
    let font = NSFontManager.shared.font(withFamily: "Lucida Grande", traits: .boldFontMask, weight: 9, size: size)
        ?? .boldSystemFont(ofSize: size)
    let s = NSMutableAttributedString()
    for (ch, col) in [("f", cF), ("b", cB), ("a", cA)] {
        s.append(NSAttributedString(string: ch, attributes: [.font: font, .foregroundColor: col]))
    }
    return s
}

func writePNG(_ cg: CGImage, _ path: String) {
    let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: path) as CFURL,
                                               UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, cg, nil)
    CGImageDestinationFinalize(dest)
}

// 1. Transparent wordmark
do {
    let size: CGFloat = 820
    let a = attr(size)
    let tsz = a.size()
    let padX = size*0.10, padY = size*0.06
    let W = Int(ceil(tsz.width + padX*2)), H = Int(ceil(tsz.height + padY*2))
    let cs = CGColorSpaceCreateDeviceRGB()
    let ctx = CGContext(data: nil, width: W, height: H, bitsPerComponent: 8, bytesPerRow: 0,
                        space: cs, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
    a.draw(at: NSPoint(x: padX, y: padY))
    NSGraphicsContext.restoreGraphicsState()
    writePNG(ctx.makeImage()!, "\(outDir)/fba_wordmark.png")
    print("wrote \(outDir)/fba_wordmark.png  (\(W)x\(H), transparent)")
}

// 2. White, no-alpha app icon (1024)
do {
    let S = 1024
    let target = CGFloat(S) * 0.78
    let probe = attr(100).size().width
    let a = attr(100 * target / probe)
    let tsz = a.size()
    let cs = CGColorSpaceCreateDeviceRGB()
    let ctx = CGContext(data: nil, width: S, height: S, bitsPerComponent: 8, bytesPerRow: 0,
                        space: cs, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: S, height: S))
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
    a.draw(at: NSPoint(x: (CGFloat(S)-tsz.width)/2, y: (CGFloat(S)-tsz.height)/2))
    NSGraphicsContext.restoreGraphicsState()
    writePNG(ctx.makeImage()!, "\(outDir)/fba_appicon_1024.png")
    print("wrote \(outDir)/fba_appicon_1024.png  (1024x1024, white, no alpha)")
}
