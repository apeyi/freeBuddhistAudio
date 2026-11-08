import SwiftUI

/// Draws SVG path data (absolute M, L, H, V, C, Z, as Figma exports) scaled into the frame, so the
/// placeholder tile symbols and Connect logos stay sharp at any size. Mirrors Android's
/// VectorDrawables, which take the same path strings.
struct SVGPathShape: Shape {
    let paths: [String]
    let viewBox: CGSize

    func path(in rect: CGRect) -> Path {
        var out = Path()
        for d in paths { out.addPath(Self.parse(d)) }
        let s = min(rect.width / viewBox.width, rect.height / viewBox.height)
        let dx = rect.minX + (rect.width - viewBox.width * s) / 2
        let dy = rect.minY + (rect.height - viewBox.height * s) / 2
        return out.applying(CGAffineTransform(a: s, b: 0, c: 0, d: s, tx: dx, ty: dy))
    }

    /// Absolute commands only; numbers separated by spaces, commas or signs.
    static func parse(_ d: String) -> Path {
        var p = Path()
        var tokens: [String] = []
        var num = ""
        func flush() { if !num.isEmpty { tokens.append(num); num = "" } }
        for ch in d {
            if ch.isLetter && ch != "e" && ch != "E" { flush(); tokens.append(String(ch)) }
            else if ch == "," || ch == " " || ch == "\n" || ch == "\t" { flush() }
            else if ch == "-" && !num.isEmpty && !(num.last == "e" || num.last == "E") { flush(); num = "-" }
            else if ch == "." && num.contains(".") && !(num.contains("e") || num.contains("E")) { flush(); num = "." }
            else { num.append(ch) }
        }
        flush()
        var i = 0
        var cmd = "M"
        var current = CGPoint.zero
        func next() -> CGFloat { defer { i += 1 }; return CGFloat(Double(tokens[i]) ?? 0) }
        while i < tokens.count {
            if let c = tokens[i].first, c.isLetter { cmd = tokens[i]; i += 1; if cmd == "Z" || cmd == "z" { p.closeSubpath(); continue } }
            guard i < tokens.count else { break }
            switch cmd {
            case "M": current = CGPoint(x: next(), y: next()); p.move(to: current); cmd = "L"
            case "L": current = CGPoint(x: next(), y: next()); p.addLine(to: current)
            case "H": current.x = next(); p.addLine(to: current)
            case "V": current.y = next(); p.addLine(to: current)
            case "C":
                let c1 = CGPoint(x: next(), y: next()), c2 = CGPoint(x: next(), y: next())
                current = CGPoint(x: next(), y: next())
                p.addCurve(to: current, control1: c1, control2: c2)
            default: i += 1   // unsupported command: skip its number
            }
        }
        return p
    }
}

extension TileSymbol {
    var shape: SVGPathShape { SVGPathShape(paths: paths, viewBox: viewBox) }
}
