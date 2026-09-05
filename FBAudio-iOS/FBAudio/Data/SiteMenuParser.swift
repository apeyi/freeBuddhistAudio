import Foundation

/// Parses the website's `document.__FBA__.sidebar_menu` JSON into MenuNodes.
/// Pure — no network — so it can be unit tested against a captured sample.
enum SiteMenuParser {

    static func parse(json: Any) -> [MenuNode] {
        let items: [Any]
        if let dict = json as? [String: Any], let arr = dict["items"] as? [Any] {
            items = arr
        } else if let arr = json as? [Any] {
            items = arr
        } else {
            return []
        }
        return items.compactMap(parseNode)
    }

    static func parse(jsonString: String) -> [MenuNode] {
        guard let data = jsonString.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) else { return [] }
        return parse(json: obj)
    }

    private static func parseNode(_ any: Any) -> MenuNode? {
        guard let obj = any as? [String: Any] else { return nil }
        guard let rawLabel = (obj["label"] as? String) ?? (obj["name"] as? String) else { return nil }
        let label = HTMLEntities.unescape(rawLabel).trimmingCharacters(in: .whitespaces)
        let link = ((obj["link"] as? String) ?? (obj["href"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
        let om: Bool
        if let b = obj["om"] as? Bool { om = b }
        else if let n = obj["om"] as? Int { om = n != 0 }
        else { om = false }
        let childrenArr = (obj["children"] as? [Any]) ?? (obj["subMenu"] as? [Any]) ?? []
        return MenuNode(label: label, link: link, om: om, children: childrenArr.compactMap(parseNode))
    }

    /// Find the top-level section by label (case-insensitive), e.g. "themes", "people".
    static func section(_ menu: [MenuNode], _ label: String) -> MenuNode? {
        menu.first { $0.label.caseInsensitiveCompare(label) == .orderedSame }
    }

    /// The curated collections shown as tiles: every `/collection/` entry under the
    /// "collections" section, flattened one level. Index pages and "latest" are
    /// excluded — they have their own rows.
    static func collectionTiles(_ menu: [MenuNode]) -> [MenuNode] {
        guard let section = section(menu, "collections") else { return [] }
        var out: [MenuNode] = []
        var seen = Set<String>()
        for node in section.children {
            if let slug = node.collectionSlug, seen.insert(slug).inserted { out.append(node) }
            for child in node.children {
                if let slug = child.collectionSlug, seen.insert(slug).inserted { out.append(child) }
            }
        }
        return out
    }
}

/// Minimal HTML entity decoding for menu labels ("&ntilde;", "&amp;", "&#39;").
enum HTMLEntities {
    private static let named: [String: String] = [
        "amp": "&", "lt": "<", "gt": ">", "quot": "\"", "apos": "'", "nbsp": " ",
        "ntilde": "ñ", "eacute": "é", "aacute": "á", "iacute": "í", "oacute": "ó", "uacute": "ú",
        "Auml": "Ä", "auml": "ä", "ouml": "ö", "uuml": "ü", "szlig": "ß", "ccedil": "ç",
        "agrave": "à", "egrave": "è", "atilde": "ã", "otilde": "õ", "rsquo": "’", "lsquo": "‘",
        "ndash": "–", "mdash": "—",
    ]

    static func unescape(_ s: String) -> String {
        guard s.contains("&") else { return s }
        var out = ""
        var i = s.startIndex
        while i < s.endIndex {
            if s[i] == "&", let semi = s[i...].firstIndex(of: ";"), s.distance(from: i, to: semi) <= 10 {
                let body = String(s[s.index(after: i)..<semi])
                if body.hasPrefix("#x"), let code = UInt32(body.dropFirst(2), radix: 16), let sc = Unicode.Scalar(code) {
                    out.unicodeScalars.append(sc); i = s.index(after: semi); continue
                }
                if body.hasPrefix("#"), let code = UInt32(body.dropFirst()), let sc = Unicode.Scalar(code) {
                    out.unicodeScalars.append(sc); i = s.index(after: semi); continue
                }
                if let rep = named[body] { out += rep; i = s.index(after: semi); continue }
            }
            out.append(s[i])
            i = s.index(after: i)
        }
        return out
    }
}
